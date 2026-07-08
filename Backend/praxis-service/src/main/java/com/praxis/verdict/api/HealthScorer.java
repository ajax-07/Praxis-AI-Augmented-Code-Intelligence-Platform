package com.praxis.verdict.api;

import com.praxis.verdict.api.dto.ScoringInput;

/** SCORING stage: 0 (worst) .. 100 (best) repository health. */
public interface HealthScorer {
    int score(ScoringInput input);
}
