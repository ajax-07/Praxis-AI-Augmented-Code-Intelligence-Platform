package com.praxis.prism.api.dto;

import java.util.UUID;

/** One row in the dashboard's file tree/list. */
public record FileSummary(
        UUID fileResultId,
        String path,
        Integer loc,
        Integer complexity,
        Integer classCount,
        long findingCount
) {}
