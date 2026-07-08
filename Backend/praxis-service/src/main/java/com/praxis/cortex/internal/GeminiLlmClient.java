package com.praxis.cortex.internal;

import com.praxis.cortex.config.CortexProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

/**
 * Google Gemini generateContent (v1beta/models/{model}:generateContent).
 * Requires GEMINI_API_KEY, sent as the x-goog-api-key header (kept out of the
 * URL so it never lands in access logs). Returns usageMetadata token counts.
 */
@Component
@ConditionalOnProperty(name = "praxis.cortex.provider", havingValue = "gemini")
public class GeminiLlmClient extends AbstractHttpLlmClient {

    private final String model;
    private final String apiKey;

    public GeminiLlmClient(CortexProperties props) {
        super(props.getGemini().getBaseUrl(), props.getGemini().getTimeoutSeconds(), props.getTemperature());
        this.model = props.getGemini().getModel();
        this.apiKey = props.getGemini().getApiKey();
        log.info("Cortex using Gemini provider: model={}", model);
    }

    @Override
    public LlmResponse complete(String systemPrompt, String userPrompt) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new LlmUnavailableException("Gemini API key not configured (GEMINI_API_KEY)", null);
        }

        // Gemini takes the system prompt separately and the user turn as "contents".
        Map<String, Object> body = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", systemPrompt))),
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", userPrompt)))),
                "generationConfig", Map.of("temperature", temperature));

        JsonNode r = postJson("/models/" + model + ":generateContent", body,
                h -> h.set("x-goog-api-key", apiKey));

        String content = requireContent(
                r.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText(null));
        int tokensIn = r.path("usageMetadata").path("promptTokenCount").asInt(estimateTokens(systemPrompt + userPrompt));
        int tokensOut = r.path("usageMetadata").path("candidatesTokenCount").asInt(estimateTokens(content));
        return new LlmResponse(content, "GEMINI", model, tokensIn, tokensOut);
    }

    @Override
    protected String providerName() { return "Gemini"; }
}
