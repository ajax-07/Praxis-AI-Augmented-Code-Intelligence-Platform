package com.praxis.cortex.internal;

/** A provider-agnostic completion result plus the accounting Cortex records. */
public record LlmResponse(String content, String provider, String model, int tokensIn, int tokensOut) {}
