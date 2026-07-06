package com.praxis.intake.internal;

public class UnsafeArchiveException extends IntakeException {
    public UnsafeArchiveException(String message) {
        super("UNSAFE_ARCHIVE", message);
    }
}
