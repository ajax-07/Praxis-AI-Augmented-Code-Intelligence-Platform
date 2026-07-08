/**
 * Cortex module: the AI layer. For the high-risk units the Conductor funnel
 * selects, it asks an LLM for an explanation + refactoring suggestion and
 * records them as AI findings (via Prism's FindingWriter) plus a cost row.
 *
 * The provider is behind LlmClient. The DEFAULT is a deterministic StubLlmClient
 * so the whole pipeline runs offline with no API keys — swap in a real provider
 * (Spring AI / Ollama) later without touching CortexService. Depends on
 * "prism :: api" (to fetch/write findings) and common.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Cortex",
    allowedDependencies = {"prism :: api", "common"}
)
package com.praxis.cortex;
