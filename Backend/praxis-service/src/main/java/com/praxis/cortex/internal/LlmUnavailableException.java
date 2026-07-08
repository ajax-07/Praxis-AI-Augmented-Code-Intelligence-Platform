package com.praxis.cortex.internal;

/**
 * Thrown when a provider is unreachable/misconfigured (connection refused,
 * timeout, missing API key). Distinct from a per-unit error: it signals the
 * WHOLE batch should stop trying and degrade to static-only, instead of eating
 * one timeout per remaining unit.
 */
public class LlmUnavailableException extends RuntimeException {
    public LlmUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
