package com.praxis.conductor.api;

import com.praxis.conductor.api.dto.AnalysisSummary;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Read access to analyses for OTHER modules. Chronicle uses find(...) to verify
 * tenant ownership before serving a dashboard, and list(...) for the history view.
 */
public interface AnalysisQuery {
    Optional<AnalysisSummary> find(UUID analysisId, UUID tenantId);
    List<AnalysisSummary> list(UUID tenantId);
}
