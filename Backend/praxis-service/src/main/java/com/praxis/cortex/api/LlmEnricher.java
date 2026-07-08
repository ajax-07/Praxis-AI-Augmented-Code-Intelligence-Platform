package com.praxis.cortex.api;

import com.praxis.cortex.api.dto.EnrichCommand;

/**
 * SUMMARIZING stage entry point: enrich the selected units with AI explanations
 * and suggestions. Returns the number of units successfully enriched.
 */
public interface LlmEnricher {
    int enrich(EnrichCommand command);
}
