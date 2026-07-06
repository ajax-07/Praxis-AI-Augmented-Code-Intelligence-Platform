package com.praxis.intake.internal;

public class FetchFailedException extends IntakeException {
    public FetchFailedException(String message, Throwable cause) {
        super("FETCH_FAILED", message, cause);
    }
}
