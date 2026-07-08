package com.praxis.cortex.api.dto;

import java.util.List;
import java.util.UUID;

public record EnrichCommand(UUID analysisId, UUID tenantId, List<UnitToReview> units) {}
