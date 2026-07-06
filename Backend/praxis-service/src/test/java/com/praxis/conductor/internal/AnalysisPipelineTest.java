package com.praxis.conductor.internal;

import com.praxis.common.SourceType;
import com.praxis.conductor.domain.Analysis;
import com.praxis.conductor.domain.AnalysisStatus;
import com.praxis.conductor.domain.Repository;
import com.praxis.intake.api.SourceFetcher;
import com.praxis.intake.api.dto.FetchResult;
import com.praxis.intake.api.dto.JavaFile;
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
 * Proves the orchestration flow stays intact after wiring in real FETCHING:
 *  - the whole pipeline reaches COMPLETE
 *  - FETCHING actually calls Intake with the repository's source
 *  - the ephemeral workspace is ALWAYS released (success AND failure)
 *  - a redelivered job on a terminal analysis is a no-op
 * All collaborators are mocked — no DB, no Redis, no filesystem.
 */
@ExtendWith(MockitoExtension.class)
class AnalysisPipelineTest {

    @Mock private AnalysisRepository analysisRepository;
    @Mock private CodeRepositoryRepository repositoryRepository;
    @Mock private SourceFetcher sourceFetcher;
    @Mock private ProgressPublisher progress;

    private AnalysisPipeline pipeline;

    private final UUID analysisId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();
    private final UUID repoId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        pipeline = new AnalysisPipeline(analysisRepository, repositoryRepository, sourceFetcher, progress);
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

    @Test
    void happyPathRunsAllStagesToCompleteAndReleasesWorkspace() {
        Analysis analysis = freshAnalysis();
        when(analysisRepository.findById(analysisId)).thenReturn(Optional.of(analysis));
        when(repositoryRepository.findById(repoId)).thenReturn(Optional.of(repo()));
        FetchResult result = fetchResult();
        when(sourceFetcher.fetch(any())).thenReturn(result);

        pipeline.run(analysisId);

        assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.COMPLETE);
        assertThat(analysis.getHealthScore()).isNotNull();
        verify(sourceFetcher).fetch(any());
        verify(sourceFetcher).release(result);          // workspace cleaned up
        verify(progress, atLeastOnce()).publish(any()); // progress emitted
    }

    @Test
    void fetchFieldsComeFromTheRepositoryRow() {
        Analysis analysis = freshAnalysis();
        when(analysisRepository.findById(analysisId)).thenReturn(Optional.of(analysis));
        when(repositoryRepository.findById(repoId)).thenReturn(Optional.of(repo()));
        when(sourceFetcher.fetch(any())).thenReturn(fetchResult());

        pipeline.run(analysisId);

        var cmd = ArgumentCaptor.forClass(com.praxis.intake.api.dto.FetchCommand.class);
        verify(sourceFetcher).fetch(cmd.capture());
        assertThat(cmd.getValue().sourceType()).isEqualTo(SourceType.GITHUB);
        assertThat(cmd.getValue().sourceRef()).isEqualTo("https://github.com/x/y");
        assertThat(cmd.getValue().analysisId()).isEqualTo(analysisId);
    }

    @Test
    void failureDuringFetchMarksFailedAndDoesNotReleaseANullWorkspace() {
        Analysis analysis = freshAnalysis();
        when(analysisRepository.findById(analysisId)).thenReturn(Optional.of(analysis));
        when(repositoryRepository.findById(repoId)).thenReturn(Optional.of(repo()));
        doThrow(new RuntimeException("clone timed out")).when(sourceFetcher).fetch(any());

        pipeline.run(analysisId);

        assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(analysis.getErrorMessage()).contains("clone timed out");
        // fetch threw before the context captured a result, so release is never called
        verify(sourceFetcher, times(0)).release(any());
    }

    @Test
    void redeliveredTerminalJobIsANoOp() {
        Analysis analysis = freshAnalysis();
        analysis.markComplete(80); // already terminal
        when(analysisRepository.findById(analysisId)).thenReturn(Optional.of(analysis));

        pipeline.run(analysisId);

        verify(sourceFetcher, times(0)).fetch(any());
        verify(repositoryRepository, times(0)).findById(any());
    }
}
