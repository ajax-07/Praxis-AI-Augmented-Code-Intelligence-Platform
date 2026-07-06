package com.praxis.conductor.api.dto;

import com.praxis.conductor.domain.AnalysisStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * The payload pushed over Redis Pub/Sub and then out to the browser as an SSE
 * message. Small and self-describing so any app instance can relay it without
 * a database lookup.
 */
public record ProgressEvent(
        UUID analysisId,
        AnalysisStatus status,
        String message,
        Instant at
) {
    public static ProgressEvent of(UUID analysisId, AnalysisStatus status, String message) {
        return new ProgressEvent(analysisId, status, message, Instant.now());
    }
}
