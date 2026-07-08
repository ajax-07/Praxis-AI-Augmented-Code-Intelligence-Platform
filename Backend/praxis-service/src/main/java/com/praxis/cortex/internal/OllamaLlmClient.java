package com.praxis.cortex.internal;

import com.praxis.cortex.config.CortexProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

/**
 * Local models via Ollama's /api/chat (stream=false). No API key, no per-token
 * cost — the recommended default for self-hosted/offline deployments.
 *
 * Best model is config-driven (praxis.cortex.ollama.model); qwen2.5-coder is a
 * strong default for code review. Ollama returns real token counts
 * (prompt_eval_count / eval_count) which we record for the cost ledger.
 */
@Component
@ConditionalOnProperty(name = "praxis.cortex.provider", havingValue = "ollama")
public class OllamaLlmClient extends AbstractHttpLlmClient {

    private final String model;

    public OllamaLlmClient(CortexProperties props) {
        super(props.getOllama().getBaseUrl(), props.getOllama().getTimeoutSeconds(), props.getTemperature());
        this.model = props.getOllama().getModel();
        log.info("Cortex using Ollama provider: model={} baseUrl={}",
                model, props.getOllama().getBaseUrl());
    }

    @Override
    public LlmResponse complete(String systemPrompt, String userPrompt) {
        Map<String, Object> body = Map.of(
                "model", model,
                "stream", false,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)),
                "options", Map.of("temperature", temperature));

        JsonNode r = postJson("/api/chat", body, h -> { });

        String content = requireContent(r.path("message").path("content").asText(null));
        int tokensIn = r.path("prompt_eval_count").asInt(estimateTokens(systemPrompt + userPrompt));
        int tokensOut = r.path("eval_count").asInt(estimateTokens(content));
        return new LlmResponse(content, "OLLAMA", model, tokensIn, tokensOut);
    }

    @Override
    protected String providerName() { return "Ollama"; }
}
