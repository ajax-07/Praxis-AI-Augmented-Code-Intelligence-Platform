package com.praxis.conductor.api.dto;

import com.praxis.conductor.domain.AnalysisStatus;

import java.util.UUID;

public record StartAnalysisResponse(UUID analysisId, AnalysisStatus status) {
}
