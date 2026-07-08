package com.praxis.cortex.internal;

import tools.jackson.databind.JsonNode;
import com.praxis.cortex.config.CortexProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * OpenAI Chat Completions (/v1/chat/completions). Requires OPENAI_API_KEY.
 * Returns exact usage counts (usage.prompt_tokens / completion_tokens).
 */
@Component
@ConditionalOnProperty(name = "praxis.cortex.provider", havingValue = "openai")
public class OpenAiLlmClient extends AbstractHttpLlmClient {

    private final String model;
    private final String apiKey;

    public OpenAiLlmClient(CortexProperties props) {
        super(props.getOpenai().getBaseUrl(), props.getOpenai().getTimeoutSeconds(), props.getTemperature());
        this.model = props.getOpenai().getModel();
        this.apiKey = props.getOpenai().getApiKey();
        log.info("Cortex using OpenAI provider: model={}", model);
    }

    @Override
    public LlmResponse complete(String systemPrompt, String userPrompt) {
        // Missing key -> unavailable (degrade to static-only) with a clear message,
        // rather than firing a request that will 401 for every unit.
        if (apiKey == null || apiKey.isBlank()) {
            throw new LlmUnavailableException("OpenAI API key not configured (OPENAI_API_KEY)", null);
        }

        Map<String, Object> body = Map.of(
                "model", model,
                "temperature", temperature,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)));

        JsonNode r = postJson("/chat/completions", body,
                h -> h.setBearerAuth(apiKey));

        String content = requireContent(
                r.path("choices").path(0).path("message").path("content").asText(null));
        int tokensIn = r.path("usage").path("prompt_tokens").asInt(estimateTokens(systemPrompt + userPrompt));
        int tokensOut = r.path("usage").path("completion_tokens").asInt(estimateTokens(content));
        return new LlmResponse(content, "OPENAI", model, tokensIn, tokensOut);
    }

    @Override
    protected String providerName() { return "OpenAI"; }
}
