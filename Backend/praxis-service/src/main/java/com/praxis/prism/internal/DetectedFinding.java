package com.praxis.prism.internal;

import com.praxis.prism.domain.Severity;

/** Internal carrier for a finding before it's persisted as an IssueFinding. */
public record DetectedFinding(String type, Severity severity, String message,
                              Integer startLine, Integer endLine) {}
