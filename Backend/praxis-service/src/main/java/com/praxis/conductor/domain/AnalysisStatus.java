package com.praxis.conductor.domain;

/**
 * The analysis lifecycle. Progresses strictly downward through the working
 * stages, then lands on exactly one terminal state. The UI progress bar is
 * a direct reflection of this enum.
 */
public enum AnalysisStatus {
    QUEUED,        // persisted, sitting on the Redis stream, not yet picked up
    FETCHING,      // Intake: clone / unzip (SIMULATED for now)
    PARSING,       // Prism: AST + metrics + patterns + risk score (SIMULATED)
    ANALYZING,     // Conductor funnel: select high-risk units for the LLM (SIMULATED)
    SUMMARIZING,   // Cortex: explanations / refactors / JavaDoc (SIMULATED)
    SCORING,       // Verdict: aggregate Repository Health Score (SIMULATED)
    COMPLETE,      // terminal: success
    FAILED;        // terminal: an error occurred (see analysis.error_message)

    public boolean isTerminal() {
        return this == COMPLETE || this == FAILED;
    }

    /** The ordered working stages a job walks through before COMPLETE. */
    public static AnalysisStatus[] workingStages() {
        return new AnalysisStatus[] { FETCHING, PARSING, ANALYZING, SUMMARIZING, SCORING };
    }
}
