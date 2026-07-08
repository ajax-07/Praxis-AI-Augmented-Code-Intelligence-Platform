package com.praxis.prism.internal;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Turns metrics + detected findings into a 0..100 risk score. This score is the
 * SINGLE lever the Conductor funnel uses to decide which units go to the (costly)
 * LLM — so it's intentionally simple, monotonic, and easy to reason about.
 */
@Component
public class RiskScorer {

    /** Method risk: driven by complexity and length, bumped by any anti-patterns. */
    public int scoreMethod(int complexity, int loc, List<DetectedFinding> findings) {
        int score = 0;
        score += Math.min(60, complexity * 6);   // complexity dominates
        score += Math.min(25, loc / 4);          // length contributes
        score += findingBump(findings);
        return clamp(score);
    }

    /** Class risk: driven by size, bumped by anti-patterns like God Object. */
    public int scoreClass(int methodCount, int loc, List<DetectedFinding> findings) {
        int score = 0;
        score += Math.min(40, methodCount * 2);
        score += Math.min(30, loc / 20);
        score += findingBump(findings);
        return clamp(score);
    }

    private int findingBump(List<DetectedFinding> findings) {
        int bump = 0;
        for (DetectedFinding f : findings) {
            bump += switch (f.severity()) {
                case CRITICAL -> 40;
                case MAJOR -> 25;
                case MINOR -> 10;
                case INFO -> 0;
            };
        }
        return bump;
    }

    private int clamp(int score) {
        return Math.max(0, Math.min(100, score));
    }
}
