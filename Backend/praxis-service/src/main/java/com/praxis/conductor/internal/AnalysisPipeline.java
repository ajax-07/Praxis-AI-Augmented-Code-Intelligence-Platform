package com.praxis.conductor.internal;

import com.praxis.conductor.api.dto.ProgressEvent;
import com.praxis.conductor.domain.Analysis;
import com.praxis.conductor.domain.AnalysisStatus;
import com.praxis.conductor.domain.Repository;
import com.praxis.intake.api.SourceFetcher;
import com.praxis.intake.api.dto.FetchCommand;
import com.praxis.intake.api.dto.FetchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Runs one analysis through its stages, threading a PipelineContext so each
 * stage's output feeds the next. FETCHING is now REAL (delegates to Intake);
 * PARSING / ANALYZING / SUMMARIZING / SCORING are still simulated until their
 * modules land. The ephemeral workspace produced by FETCHING is ALWAYS released
 * in the finally block — success or failure — so temp disk never leaks.
 *
 * Plug-in seams (unchanged orchestration when each becomes real):
 *   FETCHING    -> intake.api.SourceFetcher            [DONE]
 *   PARSING     -> prism.api.StaticAnalyzer            [next]
 *   ANALYZING   -> conductor funnel over Prism output
 *   SUMMARIZING -> cortex.api.LlmEnricher
 *   SCORING     -> verdict.api.HealthScorer
 */
@Component
public class AnalysisPipeline {

    private static final Logger log = LoggerFactory.getLogger(AnalysisPipeline.class);

    private final AnalysisRepository analysisRepository;
    private final CodeRepositoryRepository repositoryRepository;
    private final SourceFetcher sourceFetcher;
    private final ProgressPublisher progress;

    public AnalysisPipeline(
            AnalysisRepository analysisRepository,
            CodeRepositoryRepository repositoryRepository,
            SourceFetcher sourceFetcher,
            ProgressPublisher progress
    ) {
        this.analysisRepository = analysisRepository;
        this.repositoryRepository = repositoryRepository;
        this.sourceFetcher = sourceFetcher;
        this.progress = progress;
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
            runSimulated(analysis, AnalysisStatus.PARSING);
            runSimulated(analysis, AnalysisStatus.ANALYZING);
            runSimulated(analysis, AnalysisStatus.SUMMARIZING);
            runSimulated(analysis, AnalysisStatus.SCORING);

            int healthScore = ThreadLocalRandom.current().nextInt(55, 96); // placeholder until Verdict is real
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

    // ---- REAL stage ----

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

    // ---- SIMULATED stages (replaced as their modules land) ----

    private void runSimulated(Analysis analysis, AnalysisStatus stage) throws InterruptedException {
        transition(analysis, stage, humanLabel(stage));
        Thread.sleep(ThreadLocalRandom.current().nextLong(500, 1000)); // stand-in for real work
    }

    // ---- shared helpers ----

    private void transition(Analysis analysis, AnalysisStatus stage, String label) {
        analysis.advanceTo(stage);
        analysisRepository.save(analysis);
        progress.publish(ProgressEvent.of(analysis.getId(), stage, label));
        log.debug("Analysis {} -> {}", analysis.getId(), stage);
    }

    private String humanLabel(AnalysisStatus stage) {
        return switch (stage) {
            case PARSING -> "Parsing and measuring code…";
            case ANALYZING -> "Selecting high-risk code for review…";
            case SUMMARIZING -> "Generating explanations and refactors…";
            case SCORING -> "Computing repository health score…";
            default -> stage.name();
        };
    }
}
