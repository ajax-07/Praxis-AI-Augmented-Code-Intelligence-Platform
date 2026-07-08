package com.praxis.prism.api.dto;

/** Finding tallies for an analysis, consumed by Verdict to compute the score. */
public record SeverityCounts(long critical, long major, long minor, long info) {}
