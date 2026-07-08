package com.praxis.prism.api.dto;

import java.util.UUID;

/**
 * A parsed code unit as seen by the pipeline. sourceSnippet is the unit's own
 * text, carried in-memory so the Conductor funnel can hand high-risk units
 * straight to Cortex without re-reading the (now-deleted) workspace.
 */
public record UnitSummary(
        UUID codeUnitId,
        UUID fileResultId,
        String unitType,
        String name,
        int startLine,
        int endLine,
        int riskScore,
        String sourceSnippet
) {}
