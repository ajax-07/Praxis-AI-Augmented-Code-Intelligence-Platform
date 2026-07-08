package com.praxis.prism.api.dto;

import java.util.List;

/**
 * What PARSING produces: every unit (with its risk score) plus repo-level
 * aggregates. The pipeline funnels `units` by riskScore and feeds the
 * aggregates into scoring.
 */
public record StaticAnalysisResult(
        List<UnitSummary> units,
        int totalFiles,
        int totalLoc,
        double averageComplexity
) {}
