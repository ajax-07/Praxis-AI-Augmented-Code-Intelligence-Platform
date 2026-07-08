package com.praxis.cortex.internal;

import com.praxis.cortex.api.dto.EnrichCommand;
import com.praxis.cortex.api.dto.UnitToReview;
import com.praxis.cortex.config.CortexProperties;
import com.praxis.prism.api.FindingWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cortex must write one AI finding + one cost row per unit, and must be resilient:
 * a single failing unit cannot abort the rest of the batch.
 */
@ExtendWith(MockitoExtension.class)
class CortexServiceTest {

    @Mock private LlmClient llmClient;
    @Mock private FindingWriter findingWriter;
    @Mock private LlmCallRepository llmCalls;

    private CortexService cortex;

    private final UUID analysisId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        cortex = new CortexService(llmClient, findingWriter, llmCalls, new CortexProperties());
        lenient().when(llmClient.complete(anyString(), anyString()))
                .thenReturn(new LlmResponse("review text", "STUB", "stub-v1", 10, 20));
    }

    private EnrichCommand cmd(UnitToReview... units) {
        return new EnrichCommand(analysisId, tenantId, List.of(units));
    }

    private UnitToReview unit(String name) {
        return new UnitToReview(UUID.randomUUID(), "METHOD", name, "void " + name + "(){}");
    }

    @Test
    void writesOneFindingAndOneCostRowPerUnit() {
        int enriched = cortex.enrich(cmd(unit("a"), unit("b")));

        assertThat(enriched).isEqualTo(2);
        verify(findingWriter, times(2)).addAiFindings(eq(analysisId), any(), any());
        verify(llmCalls, times(2)).save(any());
    }

    @Test
    void providerUnavailableShortCircuitsTheBatch() {
        // A down provider must NOT be retried per unit — the batch stops immediately
        // so the analysis degrades to static-only fast, without N timeouts.
        when(llmClient.complete(anyString(), anyString()))
                .thenThrow(new LlmUnavailableException("Ollama unreachable", null));

        int enriched = cortex.enrich(cmd(unit("a"), unit("b"), unit("c")));

        assertThat(enriched).isZero();
        verify(llmClient, times(1)).complete(anyString(), anyString()); // stopped after the first failure
        verify(findingWriter, never()).addAiFindings(any(), any(), any());
    }

    @Test
    void oneFailingUnitDoesNotAbortTheBatch() {
        // first unit's write blows up; the second must still be processed
        doThrow(new RuntimeException("boom"))
                .doNothing()
                .when(findingWriter).addAiFindings(any(), any(), any());

        int enriched = cortex.enrich(cmd(unit("a"), unit("b")));

        assertThat(enriched).isEqualTo(1);
        verify(llmCalls, times(1)).save(any()); // only the successful unit records cost
    }
}
