package com.praxis.intake.api.dto;

import com.praxis.common.SourceType;

import java.util.UUID;

/**
 * What Conductor hands Intake to fetch a codebase. analysisId is used only to
 * namespace the ephemeral workspace directory and to tag logs — Intake never
 * touches Conductor's Analysis entity.
 *
 * sourceRef meaning depends on sourceType:
 *   GITHUB -> a clonable repository URL (https://github.com/owner/repo[.git])
 *   ZIP    -> an absolute path to a .zip file already on disk (upload wiring
 *             is a later concern; for now the caller provides a valid path)
 */
public record FetchCommand(UUID analysisId, SourceType sourceType, String sourceRef) {
}
