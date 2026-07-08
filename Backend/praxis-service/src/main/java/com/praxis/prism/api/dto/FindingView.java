package com.praxis.prism.api.dto;

/** A finding as shown in the dashboard side-panel. */
public record FindingView(
        String type,
        String severity,
        String source,     // STATIC | AI
        String message,
        String suggestion,
        Integer startLine,
        Integer endLine
) {}
