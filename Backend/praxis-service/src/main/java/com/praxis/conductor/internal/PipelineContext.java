package com.praxis.conductor.internal;

import com.praxis.intake.api.dto.FetchResult;
import com.praxis.prism.api.dto.StaticAnalysisResult;
import com.praxis.prism.api.dto.UnitSummary;

import java.util.List;
import java.util.UUID;

/**
 * Mutable, per-run scratchpad that carries a stage's OUTPUT to the next stage's
 * INPUT. Real stages are no longer independent sleeps — Intake produces a
 * workspace Prism consumes, Prism produces units the funnel filters, etc.
 *
 * Only EPHEMERAL, in-memory handles live here. DURABLE results (file_result,
 * code_unit, findings, score) are written to Postgres by their stages so they
 * survive the run and feed the dashboard — this context is discarded on return.
 */
class PipelineContext {

    private final UUID analysisId;
    private final UUID tenantId;

    private FetchResult fetchResult;              // FETCHING output, released in finally
    private StaticAnalysisResult staticResult;    // PARSING output
    private List<UnitSummary> selectedUnits;      // ANALYZING (funnel) output

    PipelineContext(UUID analysisId, UUID tenantId) {
        this.analysisId = analysisId;
        this.tenantId = tenantId;
    }

    UUID analysisId() { return analysisId; }
    UUID tenantId() { return tenantId; }

    FetchResult fetchResult() { return fetchResult; }
    void setFetchResult(FetchResult fetchResult) { this.fetchResult = fetchResult; }

    StaticAnalysisResult staticResult() { return staticResult; }
    void setStaticResult(StaticAnalysisResult staticResult) { this.staticResult = staticResult; }

    List<UnitSummary> selectedUnits() { return selectedUnits; }
    void setSelectedUnits(List<UnitSummary> selectedUnits) { this.selectedUnits = selectedUnits; }
}
