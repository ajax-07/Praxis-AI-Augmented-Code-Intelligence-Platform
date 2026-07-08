package com.praxis.cortex.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.function.Consumer;

/**
 * Shared HTTP plumbing for real providers. Uses Spring's RestClient (no Spring AI
 * dependency, so it compiles across Spring AI versions) + Jackson JsonNode for
 * forgiving response parsing.
 *
 * Error mapping is the important bit:
 *   - connection refused / timeout  -> LlmUnavailableException (stop the batch, degrade)
 *   - HTTP 4xx/5xx                   -> RuntimeException        (skip just this unit)
 */
abstract class AbstractHttpLlmClient implements LlmClient {

    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final RestClient http;
    protected final double temperature;

    protected AbstractHttpLlmClient(String baseUrl, int timeoutSeconds, double temperature) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        // If your Spring version exposes the int-millis setters instead of Duration,
        // this is the single line to adjust.
        factory.setConnectTimeout(Duration.ofSeconds(timeoutSeconds));
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        this.http = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
        this.temperature = temperature;
    }

    /** POST a JSON body and return the parsed response tree, mapping transport errors. */
    protected JsonNode postJson(String uri, Object body, Consumer<HttpHeaders> headers) {
        try {
            return http.post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (ResourceAccessException e) {          // I/O: refused, DNS, read timeout
            throw new LlmUnavailableException(providerName() + " unreachable: " + e.getMessage(), e);
        } catch (RestClientResponseException e) {      // non-2xx from the provider
            throw new RuntimeException(providerName() + " returned " + e.getStatusCode()
                    + ": " + truncate(e.getResponseBodyAsString()), e);
        }
    }

    /** A blank completion is treated as a per-unit failure, not a silent empty finding. */
    protected String requireContent(String content) {
        if (content == null || content.isBlank()) {
            throw new RuntimeException(providerName() + " returned an empty completion");
        }
        return content.trim();
    }

    /** Fallback token estimate (~chars/4) when a provider omits usage counts. */
    protected int estimateTokens(String text) {
        return Math.max(1, text.length() / 4);
    }

    private String truncate(String s) {
        return s == null ? "" : (s.length() > 300 ? s.substring(0, 300) + "…" : s);
    }

    /** Human-readable provider name for logs/errors (also used as LlmResponse.provider, upper-cased). */
    protected abstract String providerName();
}
