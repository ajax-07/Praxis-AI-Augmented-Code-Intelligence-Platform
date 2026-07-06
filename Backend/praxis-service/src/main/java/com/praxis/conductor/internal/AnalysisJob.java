package com.praxis.conductor.internal;

import java.util.UUID;

/**
 * The message placed on the Redis Stream. Deliberately tiny — just enough for
 * a worker (possibly on another JVM instance) to load the full Analysis from
 * the database and run it. Never put mutable state on the queue itself.
 */
public record AnalysisJob(UUID analysisId, UUID tenantId) {
}
