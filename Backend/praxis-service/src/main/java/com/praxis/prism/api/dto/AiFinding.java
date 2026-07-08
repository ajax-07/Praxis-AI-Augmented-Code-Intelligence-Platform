package com.praxis.prism.api.dto;

/** An AI-produced finding Cortex writes back through the FindingWriter. */
public record AiFinding(
        String type,
        String severity,   // one of Severity names
        String message,
        String suggestion,
        Integer startLine,
        Integer endLine
) {}
