package com.praxis.intake.internal;

/**
 * Base for everything that can go wrong while fetching a source. Unchecked on
 * purpose: fetching runs inside the Conductor worker, whose try/catch records
 * the message on the failed analysis. The `code` gives a stable machine label.
 */
public class IntakeException extends RuntimeException {

    private final String code;

    public IntakeException(String code, String message) {
        super(message);
        this.code = code;
    }

    public IntakeException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
