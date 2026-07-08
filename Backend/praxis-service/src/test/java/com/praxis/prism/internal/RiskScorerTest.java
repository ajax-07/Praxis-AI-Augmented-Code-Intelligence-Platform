package com.praxis.prism.internal;

import com.praxis.prism.domain.Severity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RiskScorerTest {

    private final RiskScorer scorer = new RiskScorer();

    @Test
    void methodRiskCombinesComplexityAndLength() {
        // min(60, 10*6=60) + min(25, 40/4=10) + 0 = 70
        assertThat(scorer.scoreMethod(10, 40, List.of())).isEqualTo(70);
    }

    @Test
    void antiPatternFindingsRaiseRisk() {
        var major = new DetectedFinding("HIGH_COMPLEXITY", Severity.MAJOR, "m", 1, 2);
        int without = scorer.scoreMethod(3, 20, List.of());
        int with = scorer.scoreMethod(3, 20, List.of(major));
        assertThat(with).isGreaterThan(without);
    }

    @Test
    void scoreIsClampedToHundred() {
        var critical = new DetectedFinding("GOD_OBJECT", Severity.CRITICAL, "m", 1, 2);
        assertThat(scorer.scoreMethod(50, 1000, List.of(critical))).isEqualTo(100);
    }

    @Test
    void classRiskCombinesSizeSignals() {
        // min(40, 25*2=50 ->40) + min(30, 1000/20=50 ->30) + 0 = 70
        assertThat(scorer.scoreClass(25, 1000, List.of())).isEqualTo(70);
    }
}
