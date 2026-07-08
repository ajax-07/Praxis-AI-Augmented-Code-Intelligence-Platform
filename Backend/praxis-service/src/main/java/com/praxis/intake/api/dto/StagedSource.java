package com.praxis.intake.api.dto;

import com.praxis.common.SourceType;

/**
 * Result of staging an uploaded file: where it landed on disk ({@code sourceRef},
 * directly consumable by SourceFetcher) and which {@link SourceType} its format
 * maps to. Supporting a new upload format later means teaching UploadStore one
 * more extension→SourceType mapping — callers stay unchanged.
 */
public record StagedSource(
        String sourceRef,
        SourceType sourceType,
        String originalFilename
) {
}
