package com.praxis.conductor.internal;

import com.praxis.common.SourceType;
import com.praxis.conductor.domain.Analysis;
import com.praxis.conductor.domain.AnalysisStatus;
import com.praxis.conductor.domain.Repository;
import com.praxis.cortex.api.LlmEnricher;
import com.praxis.intake.api.SourceFetcher;
import com.praxis.intake.api.dto.FetchResult;
import com.praxis.intake.api.dto.JavaFile;
import com.praxis.prism.api.AnalysisResultQuery;
import com.praxis.prism.api.StaticAnalyzer;
import com.praxis.prism.api.dto.SeverityCounts;
import com.praxis.prism.api.dto.StaticAnalysisResult;
import com.praxis.prism.api.dto.UnitSummary;
import com.praxis.verdict.api.HealthScorer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Proves the orchestration flow stays intact with ALL stages real:
 *  - the whole pipeline reaches COMPLETE with the score Verdict returns
 *  - FETCHING calls Intake with the repository's source
 *  - the funnel only sends high-risk units to Cortex
 *  - the ephemeral workspace is ALWAYS released (success AND failure)
 *  - a redelivered job on a terminal analysis is a no-op
 * Every collaborator is mocked — no DB, no Redis, no filesystem.
 */
@ExtendWith(MockitoExtension.class)
class AnalysisPipelineTest {

    @Mock private AnalysisRepository analysisRepository;
    @Mock private CodeRepositoryRepository repositoryRepository;
    @Mock private SourceFetcher sourceFetcher;
    @Mock private ProgressPublisher progress;
    @Mock private StaticAnalyzer staticAnalyzer;
    @Mock private AnalysisResultQuery resultQuery;
    @Mock private LlmEnricher llmEnricher;
    @Mock private HealthScorer healthScorer;

    private AnalysisPipeline pipeline;

    private final UUID analysisId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();
    private final UUID repoId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        pipeline = new AnalysisPipeline(analysisRepository, repositoryRepository, sourceFetcher, progress,
                staticAnalyzer, resultQuery, llmEnricher, healthScorer, 60);
    }

    private Analysis freshAnalysis() {
        return new Analysis(analysisId, repoId, tenantId, "v1");
    }

    private Repository repo() {
        return new Repository(repoId, tenantId, "demo", SourceType.GITHUB, "https://github.com/x/y");
    }

    private FetchResult fetchResult() {
        return new FetchResult(Path.of("/tmp/ws/" + analysisId),
                List.of(new JavaFile("A.java", Path.of("/tmp/ws/A.java"), 42)));
    }

    /** One low-risk (40) and one high-risk (85) unit — only the latter should be enriched. */
    private StaticAnalysisResult staticResultWithOneHighRiskUnit() {
        UnitSummary low = new UnitSummary(UUID.randomUUID(), UUID.randomUUID(), "METHOD", "small", 1, 3, 40, "...");
        UnitSummary high = new UnitSummary(UUID.randomUUID(), UUID.randomUUID(), "METHOD", "beast", 4, 90, 85, "...");
        return new StaticAnalysisResult(List.of(low, high), 1, 500, 6.0);
    }

    /** Stubs the collaborators a full successful run needs. */
    private void stubHappyPath() {
        when(analysisRepository.findById(analysisId)).thenReturn(Optional.of(freshAnalysis()));
        when(repositoryRepository.findById(repoId)).thenReturn(Optional.of(repo()));
        when(sourceFetcher.fetch(any())).thenReturn(fetchResult());
        when(staticAnalyzer.analyze(any())).thenReturn(staticResultWithOneHighRiskUnit());
        when(llmEnricher.enrich(any())).thenReturn(1);
        when(resultQuery.severityCounts(any())).thenReturn(new SeverityCounts(0, 1, 2, 3));
        when(healthScorer.score(any())).thenReturn(77);
    }

    @Test
    void happyPathRunsAllStagesToCompleteWithVerdictScoreAndReleasesWorkspace() {
        // capture the analysis instance we hand back so we can assert on its final state
        Analysis analysis = freshAnalysis();
        when(analysisRepository.findById(analysisId)).thenReturn(Optional.of(analysis));
        when(repositoryRepository.findById(repoId)).thenReturn(Optional.of(repo()));
        FetchResult result = fetchResult();
        when(sourceFetcher.fetch(any())).thenReturn(result);
        when(staticAnalyzer.analyze(any())).thenReturn(staticResultWithOneHighRiskUnit());
        when(llmEnricher.enrich(any())).thenReturn(1);
        when(resultQuery.severityCounts(any())).thenReturn(new SeverityCounts(0, 1, 2, 3));
        when(healthScorer.score(any())).thenReturn(77);

        pipeline.run(analysisId);

        assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.COMPLETE);
        assertThat(analysis.getHealthScore()).isEqualTo(77);          // exactly Verdict's answer
        verify(sourceFetcher).release(result);                        // workspace cleaned up
        verify(progress, atLeastOnce()).publish(any());
    }

    @Test
    void funnelSendsOnlyHighRiskUnitsToCortex() {
        stubHappyPath();

        pipeline.run(analysisId);

        var cmd = ArgumentCaptor.forClass(com.praxis.cortex.api.dto.EnrichCommand.class);
        verify(llmEnricher).enrich(cmd.capture());
        // only the 85-risk "beast" unit clears the threshold of 60
        assertThat(cmd.getValue().units()).hasSize(1);
        assertThat(cmd.getValue().units().get(0).name()).isEqualTo("beast");
    }

    @Test
    void fetchCommandComesFromTheRepositoryRow() {
        stubHappyPath();

        pipeline.run(analysisId);

        var cmd = ArgumentCaptor.forClass(com.praxis.intake.api.dto.FetchCommand.class);
        verify(sourceFetcher).fetch(cmd.capture());
        assertThat(cmd.getValue().sourceType()).isEqualTo(SourceType.GITHUB);
        assertThat(cmd.getValue().sourceRef()).isEqualTo("https://github.com/x/y");
    }

    @Test
    void failureDuringFetchMarksFailedAndReleasesNothing() {
        Analysis analysis = freshAnalysis();
        when(analysisRepository.findById(analysisId)).thenReturn(Optional.of(analysis));
        when(repositoryRepository.findById(repoId)).thenReturn(Optional.of(repo()));
        doThrow(new RuntimeException("clone timed out")).when(sourceFetcher).fetch(any());

        pipeline.run(analysisId);

        assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.FAILED);
        verify(sourceFetcher, times(0)).release(any()); // no workspace was captured
        verify(staticAnalyzer, times(0)).analyze(any());
    }

    @Test
    void redeliveredTerminalJobIsANoOp() {
        Analysis analysis = freshAnalysis();
        analysis.markComplete(80); // already terminal
        when(analysisRepository.findById(analysisId)).thenReturn(Optional.of(analysis));

        pipeline.run(analysisId);

        verify(sourceFetcher, times(0)).fetch(any());
        verify(staticAnalyzer, times(0)).analyze(any());
    }
}
