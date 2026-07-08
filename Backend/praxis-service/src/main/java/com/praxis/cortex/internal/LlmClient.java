package com.praxis.cortex.internal;

/**
 * The one thing Cortex needs from a model provider. Keeping it this small is
 * what lets us default to a stub and later drop in Spring AI / OpenAI / Ollama
 * without changing any calling code.
 */
public interface LlmClient {
    LlmResponse complete(String systemPrompt, String userPrompt);
}
