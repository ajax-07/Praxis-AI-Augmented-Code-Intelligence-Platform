package com.praxis.verdict.internal;

import com.praxis.verdict.api.dto.ScoringInput;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HealthScorerTest {

    private final HealthScorerImpl scorer = new HealthScorerImpl();

    @Test
    void cleanCodebaseScoresHundred() {
        // no findings, average complexity below the 5 threshold
        var in = new ScoringInput(10, 1000, 3.0, 0, 0, 0, 0);
        assertThat(scorer.score(in)).isEqualTo(100);
    }

    @Test
    void findingsReducePerKilolineOfCode() {
        // (critical*8 + major*3 + minor*1)/1 KLOC = (8 + 6 + 0)/1 = 14 -> 86
        var in = new ScoringInput(10, 1000, 5.0, 1, 2, 0, 0);
        assertThat(scorer.score(in)).isEqualTo(86);
    }

    @Test
    void highAverageComplexityPenaltyIsCapped() {
        // complexity penalty caps at 20 no matter how extreme
        var in = new ScoringInput(10, 1000, 100.0, 0, 0, 0, 0);
        assertThat(scorer.score(in)).isEqualTo(80);
    }

    @Test
    void scoreNeverGoesBelowZero() {
        var in = new ScoringInput(10, 1000, 5.0, 100, 0, 0, 0); // penalty 800
        assertThat(scorer.score(in)).isEqualTo(0);
    }

    @Test
    void largeCodebaseIsNormalizedBySize() {
        // same absolute findings hurt less when spread over more code
        var small = new ScoringInput(1, 1000, 5.0, 0, 5, 0, 0);   // 15/1 = 15 -> 85
        var large = new ScoringInput(50, 50000, 5.0, 0, 5, 0, 0); // 15/50 -> ~99.7 -> 100
        assertThat(scorer.score(large)).isGreaterThan(scorer.score(small));
    }
}
