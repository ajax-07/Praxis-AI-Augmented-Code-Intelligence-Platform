package com.praxis.conductor.api.dto;

import com.praxis.conductor.domain.Analysis;
import com.praxis.conductor.domain.AnalysisStatus;

import java.time.Instant;
import java.util.UUID;

/** Read model returned by GET /analyses/{id}. Never exposes the entity directly. */
public record AnalysisView(
        UUID id,
        UUID repositoryId,
        AnalysisStatus status,
        Integer healthScore,
        String errorMessage,
        Instant startedAt,
        Instant completedAt
) {
    public static AnalysisView from(Analysis a) {
        return new AnalysisView(
                a.getId(), a.getRepositoryId(), a.getStatus(), a.getHealthScore(),
                a.getErrorMessage(), a.getStartedAt(), a.getCompletedAt());
    }
}
