package com.praxis.conductor.api.dto;

import com.praxis.common.SourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * MVP shortcut: this both registers the code source and starts an analysis in
 * one call. Once Intake owns repositories, this will likely split into
 * "create repository" + "start analysis {repositoryId}".
 */
public record StartAnalysisRequest(
        @NotBlank String name,
        @NotNull SourceType sourceType,
        @NotBlank String sourceRef
) {
}
