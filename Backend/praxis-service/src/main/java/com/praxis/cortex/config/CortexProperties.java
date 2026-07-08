package com.praxis.cortex.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cortex configuration, bound from praxis.cortex.*. `provider` picks exactly one
 * LlmClient at startup (stub | ollama | openai | gemini); the matching client
 * bean is activated via @ConditionalOnProperty and the rest stay dormant.
 *
 * Each provider has its own nested block so keys/URLs/models are configured
 * independently and adding a new provider later is purely additive.
 */
@ConfigurationProperties(prefix = "praxis.cortex")
public class CortexProperties {

    /** stub (default, offline) | ollama | openai | gemini. */
    private String provider = "stub";
    /** Versioned so cached results can be invalidated when prompts change. */
    private String promptVersion = "v1";
    /** Low temperature = consistent, deterministic-ish reviews. Shared by all real providers. */
    private double temperature = 0.2;

    private final Ollama ollama = new Ollama();
    private final OpenAi openai = new OpenAi();
    private final Gemini gemini = new Gemini();

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getPromptVersion() { return promptVersion; }
    public void setPromptVersion(String promptVersion) { this.promptVersion = promptVersion; }
    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }
    public Ollama getOllama() { return ollama; }
    public OpenAi getOpenai() { return openai; }
    public Gemini getGemini() { return gemini; }

    /** Local models via Ollama — no API key, no per-token cost. */
    public static class Ollama {
        private String baseUrl = "http://localhost:11434";
        // qwen2.5-coder is among the strongest open code models; bump to :32b if the box can take it.
        private String model = "qwen2.5-coder:7b";
        private int timeoutSeconds = 60;
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String v) { this.baseUrl = v; }
        public String getModel() { return model; }
        public void setModel(String v) { this.model = v; }
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int v) { this.timeoutSeconds = v; }
    }

    /** OpenAI chat completions. Requires an API key (env OPENAI_API_KEY). */
    public static class OpenAi {
        private String baseUrl = "https://api.openai.com/v1";
        private String apiKey = "";
        private String model = "gpt-4o";        // gpt-4o-mini for lower cost
        private int timeoutSeconds = 60;
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String v) { this.baseUrl = v; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String v) { this.apiKey = v; }
        public String getModel() { return model; }
        public void setModel(String v) { this.model = v; }
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int v) { this.timeoutSeconds = v; }
    }

    /** Google Gemini generateContent. Requires an API key (env GEMINI_API_KEY). */
    public static class Gemini {
        private String baseUrl = "https://generativelanguage.googleapis.com/v1beta";
        private String apiKey = "";
        private String model = "gemini-1.5-pro"; // gemini-1.5-flash for speed/cost; newer models are drop-in via config
        private int timeoutSeconds = 60;
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String v) { this.baseUrl = v; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String v) { this.apiKey = v; }
        public String getModel() { return model; }
        public void setModel(String v) { this.model = v; }
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int v) { this.timeoutSeconds = v; }
    }
}
