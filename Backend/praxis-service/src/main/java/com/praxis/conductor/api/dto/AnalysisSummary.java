package com.praxis.conductor.api.dto;

import java.time.Instant;
import java.util.UUID;

/** A tenant-scoped analysis, as other modules (Chronicle) see it. status is a
 *  String so callers don't couple to Conductor's internal enum. */
public record AnalysisSummary(
        UUID id,
        UUID repositoryId,
        String status,
        Integer healthScore,
        Instant createdAt,
        Instant completedAt
) {}
