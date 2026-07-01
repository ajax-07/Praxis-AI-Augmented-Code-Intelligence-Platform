package com.praxis.common.dto;

/**
 * Uniform error body returned by every endpoint. `code` is a stable,
 * machine-readable identifier the frontend can switch on; `message` is
 * human-readable; `traceId` lets support correlate a report with server logs.
 */
public record ApiError(String code, String message, String traceId) {
}
