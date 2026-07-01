package com.praxis.identity.domain;

/**
 * Tenant-level policy read by the Cortex module. LOCAL_ONLY forces every
 * LLM call for this tenant through Ollama — proprietary source never
 * leaves the tenant's own infrastructure.
 */
public enum CodeResidency {
    CLOUD_ALLOWED, LOCAL_ONLY
}
