package com.praxis.conductor.internal;

import com.praxis.conductor.api.dto.ProgressEvent;
import com.praxis.conductor.domain.Analysis;
import com.praxis.conductor.domain.AnalysisStatus;
import com.praxis.conductor.domain.Repository;
import com.praxis.cortex.api.LlmEnricher;
import com.praxis.cortex.api.dto.EnrichCommand;
import com.praxis.cortex.api.dto.UnitToReview;
import com.praxis.intake.api.SourceFetcher;
import com.praxis.intake.api.dto.FetchCommand;
import com.praxis.intake.api.dto.FetchResult;
import com.praxis.prism.api.AnalysisResultQuery;
import com.praxis.prism.api.StaticAnalyzer;
import com.praxis.prism.api.dto.AnalyzeCommand;
import com.praxis.prism.api.dto.SeverityCounts;
import com.praxis.prism.api.dto.SourceFile;
import com.praxis.prism.api.dto.StaticAnalysisResult;
import com.praxis.prism.api.dto.UnitSummary;
import com.praxis.verdict.api.HealthScorer;
import com.praxis.verdict.api.dto.ScoringInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Runs one analysis through its stages, threading a PipelineContext so each
 * stage's output feeds the next. Every stage is now REAL:
 *
 *   FETCHING    -> intake.api.SourceFetcher        (clone/unzip -> .java files)
 *   PARSING     -> prism.api.StaticAnalyzer        (parse/measure/detect/persist)
 *   ANALYZING   -> the cost funnel                 (keep only high-risk units)
 *   SUMMARIZING -> cortex.api.LlmEnricher          (AI review of selected units)
 *   SCORING     -> verdict.api.HealthScorer        (0..100 health score)
 *
 * The funnel is the product's moat: cheap deterministic analysis grades every
 * unit, and only those at/above the risk threshold reach the expensive LLM.
 *
 * The ephemeral workspace from FETCHING is ALWAYS released in finally, so temp
 * disk never leaks on success OR failure.
 */
@Component
public class AnalysisPipeline {

    private static final Logger log = LoggerFactory.getLogger(AnalysisPipeline.class);

    private final AnalysisRepository analysisRepository;
    private final CodeRepositoryRepository repositoryRepository;
    private final SourceFetcher sourceFetcher;
    private final ProgressPublisher progress;
    private final StaticAnalyzer staticAnalyzer;
    private final AnalysisResultQuery resultQuery;
    private final LlmEnricher llmEnricher;
    private final HealthScorer healthScorer;

    /** Units scoring at/above this go to the LLM. Tunable via praxis.analysis.risk-threshold. */
    private final int riskThreshold;

    public AnalysisPipeline(
            AnalysisRepository analysisRepository,
            CodeRepositoryRepository repositoryRepository,
            SourceFetcher sourceFetcher,
            ProgressPublisher progress,
            StaticAnalyzer staticAnalyzer,
            AnalysisResultQuery resultQuery,
            LlmEnricher llmEnricher,
            HealthScorer healthScorer,
            @Value("${praxis.analysis.risk-threshold:60}") int riskThreshold
    ) {
        this.analysisRepository = analysisRepository;
        this.repositoryRepository = repositoryRepository;
        this.sourceFetcher = sourceFetcher;
        this.progress = progress;
        this.staticAnalyzer = staticAnalyzer;
        this.resultQuery = resultQuery;
        this.llmEnricher = llmEnricher;
        this.healthScorer = healthScorer;
        this.riskThreshold = riskThreshold;
    }

    public void run(UUID analysisId) {
        Analysis analysis = analysisRepository.findById(analysisId).orElse(null);
        if (analysis == null) {
            log.warn("Pipeline asked to run unknown analysis {}", analysisId);
            return;
        }
        if (analysis.getStatus().isTerminal()) {
            log.info("Analysis {} already {} — skipping (redelivered job)", analysisId, analysis.getStatus());
            return; // idempotent: a redelivered Redis job must not re-run a finished analysis
        }

        PipelineContext ctx = new PipelineContext(analysisId, analysis.getTenantId());
        try {
            runFetching(analysis, ctx);
            runParsing(analysis, ctx);
            runAnalyzing(analysis, ctx);
            runSummarizing(analysis, ctx);
            int healthScore = runScoring(analysis, ctx);

            analysis.markComplete(healthScore);
            analysisRepository.save(analysis);
            progress.publish(ProgressEvent.of(analysisId, AnalysisStatus.COMPLETE,
                    "Analysis complete. Health score: " + healthScore));
            log.info("Analysis {} COMPLETE (score {})", analysisId, healthScore);

        } catch (Exception ex) {
            String reason = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            analysis.markFailed(reason);
            analysisRepository.save(analysis);
            progress.publish(ProgressEvent.of(analysisId, AnalysisStatus.FAILED, "Analysis failed: " + reason));
            log.error("Analysis {} FAILED: {}", analysisId, reason, ex);

        } finally {
            // ALWAYS release the ephemeral workspace, whatever happened above.
            if (ctx.fetchResult() != null) {
                sourceFetcher.release(ctx.fetchResult());
            }
        }
    }

    // ---- FETCHING: clone/unzip the source into an ephemeral workspace ----

    private void runFetching(Analysis analysis, PipelineContext ctx) {
        transition(analysis, AnalysisStatus.FETCHING, "Fetching source code…");

        Repository repo = repositoryRepository.findById(analysis.getRepositoryId())
                .orElseThrow(() -> new IllegalStateException(
                        "Repository " + analysis.getRepositoryId() + " missing for analysis " + analysis.getId()));

        FetchResult result = sourceFetcher.fetch(
                new FetchCommand(analysis.getId(), repo.getSourceType(), repo.getSourceRef()));
        ctx.setFetchResult(result);

        progress.publish(ProgressEvent.of(analysis.getId(), AnalysisStatus.FETCHING,
                "Fetched " + result.fileCount() + " Java files"));
        log.info("Analysis {} fetched {} java files", analysis.getId(), result.fileCount());
    }

    // ---- PARSING: hand the fetched files to Prism (persists metrics + findings) ----

    private void runParsing(Analysis analysis, PipelineContext ctx) {
        transition(analysis, AnalysisStatus.PARSING, "Parsing and measuring code…");

        // Adapt Intake's JavaFile into Prism's SourceFile so Prism stays decoupled from Intake.
        List<SourceFile> sources = ctx.fetchResult().files().stream()
                .map(f -> new SourceFile(f.relativePath(), f.absolutePath()))
                .toList();

        StaticAnalysisResult result = staticAnalyzer.analyze(new AnalyzeCommand(analysis.getId(), sources));
        ctx.setStaticResult(result);

        progress.publish(ProgressEvent.of(analysis.getId(), AnalysisStatus.PARSING,
                "Parsed " + result.totalFiles() + " files into " + result.units().size() + " code units"));
    }

    // ---- ANALYZING: the funnel — keep only units worth paying an LLM for ----

    private void runAnalyzing(Analysis analysis, PipelineContext ctx) {
        transition(analysis, AnalysisStatus.ANALYZING, "Selecting high-risk code for review…");

        List<UnitSummary> selected = ctx.staticResult().units().stream()
                .filter(u -> u.riskScore() >= riskThreshold)
                .toList();
        ctx.setSelectedUnits(selected);

        progress.publish(ProgressEvent.of(analysis.getId(), AnalysisStatus.ANALYZING,
                "Selected " + selected.size() + " high-risk units (threshold " + riskThreshold + ")"));
        log.info("Analysis {} funnel: {}/{} units above risk {}",
                analysis.getId(), selected.size(), ctx.staticResult().units().size(), riskThreshold);
    }

    // ---- SUMMARIZING: Cortex reviews only the selected units ----

    private void runSummarizing(Analysis analysis, PipelineContext ctx) {
        transition(analysis, AnalysisStatus.SUMMARIZING, "Generating explanations and refactors…");

        List<UnitToReview> toReview = ctx.selectedUnits().stream()
                .map(u -> new UnitToReview(u.codeUnitId(), u.unitType(), u.name(), u.sourceSnippet()))
                .toList();

        int enriched = 0;
        if (!toReview.isEmpty()) {
            enriched = llmEnricher.enrich(new EnrichCommand(analysis.getId(), ctx.tenantId(), toReview));
        }
        progress.publish(ProgressEvent.of(analysis.getId(), AnalysisStatus.SUMMARIZING,
                "AI-reviewed " + enriched + " units"));
    }

    // ---- SCORING: Verdict turns aggregates + findings into a health score ----

    private int runScoring(Analysis analysis, PipelineContext ctx) {
        transition(analysis, AnalysisStatus.SCORING, "Computing repository health score…");

        StaticAnalysisResult sr = ctx.staticResult();
        SeverityCounts counts = resultQuery.severityCounts(analysis.getId());
        ScoringInput input = new ScoringInput(
                sr.totalFiles(), sr.totalLoc(), sr.averageComplexity(),
                counts.critical(), counts.major(), counts.minor(), counts.info());

        int score = healthScorer.score(input);
        progress.publish(ProgressEvent.of(analysis.getId(), AnalysisStatus.SCORING,
                "Health score computed: " + score));
        return score;
    }

    // ---- shared helper ----

    private void transition(Analysis analysis, AnalysisStatus stage, String label) {
        analysis.advanceTo(stage);
        analysisRepository.save(analysis);
        progress.publish(ProgressEvent.of(analysis.getId(), stage, label));
        log.debug("Analysis {} -> {}", analysis.getId(), stage);
    }
}
