package com.praxis.cortex.api.dto;

import java.util.UUID;

/** A single high-risk unit handed to Cortex for AI review. */
public record UnitToReview(UUID codeUnitId, String unitType, String name, String sourceSnippet) {}
