package com.praxis.verdict.api.dto;

/**
 * Everything Verdict needs to score an analysis, gathered by the pipeline from
 * Prism's results. Keeping it a flat value object keeps the scorer a pure function.
 */
public record ScoringInput(
        int totalFiles,
        int totalLoc,
        double averageComplexity,
        long criticalFindings,
        long majorFindings,
        long minorFindings,
        long infoFindings
) {}
