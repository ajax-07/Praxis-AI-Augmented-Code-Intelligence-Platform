package com.praxis.cortex.internal;

import com.praxis.cortex.api.LlmEnricher;
import com.praxis.cortex.api.dto.EnrichCommand;
import com.praxis.cortex.api.dto.UnitToReview;
import com.praxis.cortex.config.CortexProperties;
import com.praxis.prism.api.FindingWriter;
import com.praxis.prism.api.dto.AiFinding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * SUMMARIZING stage. For each selected unit: build a prompt, call the LlmClient,
 * store the result as an AI finding, and record a cost row. Each unit is handled
 * independently so one failing call can't abort the rest of the enrichment.
 */
@Service
public class CortexService implements LlmEnricher {

    private static final Logger log = LoggerFactory.getLogger(CortexService.class);
    private static final String TASK = "EXPLAIN_AND_REFACTOR";

    private final LlmClient llmClient;
    private final FindingWriter findingWriter;
    private final LlmCallRepository llmCalls;
    private final CortexProperties props;

    public CortexService(LlmClient llmClient, FindingWriter findingWriter,
                         LlmCallRepository llmCalls, CortexProperties props) {
        this.llmClient = llmClient;
        this.findingWriter = findingWriter;
        this.llmCalls = llmCalls;
        this.props = props;
    }

    @Override
    public int enrich(EnrichCommand command) {
        int enriched = 0;
        int total = command.units().size();
        for (int i = 0; i < total; i++) {
            UnitToReview unit = command.units().get(i);
            try {
                enrichUnit(command, unit);
                enriched++;
            } catch (LlmUnavailableException ex) {
                // Provider is down/misconfigured: stop now and let the analysis finish
                // on static findings alone, rather than waiting out a timeout per unit.
                log.warn("LLM provider unavailable — degrading analysis {} to static-only "
                                + "after {}/{} units: {}",
                        command.analysisId(), enriched, total, ex.getMessage());
                break;
            } catch (Exception ex) {
                log.warn("Cortex failed to enrich unit {} ({}) — skipping this unit",
                        unit.codeUnitId(), unit.name(), ex);
            }
        }
        log.info("Cortex enriched {}/{} units for analysis {}", enriched, total, command.analysisId());
        return enriched;
    }

    private void enrichUnit(EnrichCommand command, UnitToReview unit) {
        String system = "You are a senior Java reviewer. Explain the risk in this "
                + unit.unitType().toLowerCase() + " and suggest a concrete refactoring. Be concise.";
        String user = "Review " + unit.unitType().toLowerCase() + " '" + unit.name() + "':\n\n"
                + unit.sourceSnippet();

        LlmResponse response = llmClient.complete(system, user);

        // Persist the AI output as a finding attached to this unit.
        findingWriter.addAiFindings(command.analysisId(), unit.codeUnitId(), List.of(
                new AiFinding("AI_SUGGESTION", "INFO", "AI review of '" + unit.name() + "'",
                        response.content(), null, null)));

        // Record usage for accounting. Real providers report true token counts here;
        // cost_cents stays 0 until Ledger owns the price book (see ledger.md) and
        // maps (provider, model, tokens) -> cost. cache_hit becomes real with Recall.
        llmCalls.save(new LlmCall(UUID.randomUUID(), command.analysisId(), command.tenantId(),
                response.provider(), response.model(), TASK,
                response.tokensIn(), response.tokensOut(), 0, false));
    }
}
