package com.praxis.conductor.internal;

import com.praxis.intake.api.dto.FetchResult;

import java.util.UUID;

/**
 * Mutable, per-run scratchpad that carries a stage's OUTPUT to the next stage's
 * INPUT. It exists because real stages are no longer independent sleeps — Intake
 * produces a workspace + files that Prism will consume, etc.
 *
 * Only EPHEMERAL, in-memory handles live here (e.g. the temp workspace path).
 * DURABLE results (file_result, code_unit, findings, score) are written to
 * Postgres by their stages so they survive the run and feed the dashboard —
 * this context is discarded when run() returns.
 */
class PipelineContext {

    private final UUID analysisId;
    private final UUID tenantId;

    private FetchResult fetchResult;   // set by FETCHING, released in finally

    PipelineContext(UUID analysisId, UUID tenantId) {
        this.analysisId = analysisId;
        this.tenantId = tenantId;
    }

    UUID analysisId() { return analysisId; }
    UUID tenantId() { return tenantId; }

    FetchResult fetchResult() { return fetchResult; }
    void setFetchResult(FetchResult fetchResult) { this.fetchResult = fetchResult; }
}
