package com.praxis.intake.internal;

public class SourceTooLargeException extends IntakeException {
    public SourceTooLargeException(String message) {
        super("SOURCE_TOO_LARGE", message);
    }
}
