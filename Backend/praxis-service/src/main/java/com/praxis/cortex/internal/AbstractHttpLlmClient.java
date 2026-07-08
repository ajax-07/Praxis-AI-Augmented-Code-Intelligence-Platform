package com.praxis.cortex.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.function.Consumer;

/**
 * Shared HTTP plumbing for real providers. Uses Spring's RestClient (no Spring AI
 * dependency, so it compiles across Spring AI versions) + Jackson JsonNode for
 * forgiving response parsing.
 *
 * Error mapping is the important bit:
 *   - connect refused / DNS / read timeout -> LlmUnavailableException (stop the batch, degrade)
 *   - HTTP 4xx/5xx                          -> RuntimeException        (skip just this unit)
 *
 * The connect timeout is short (a dead host should fail fast); the read timeout
 * is the caller-supplied budget, which must be generous for local models — a 7B
 * model on CPU can take minutes to generate, and the first call also loads the
 * model into memory. Too short a read timeout makes every unit time out.
 */
abstract class AbstractHttpLlmClient implements LlmClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final RestClient http;
    protected final double temperature;

    protected AbstractHttpLlmClient(String baseUrl, int timeoutSeconds, double temperature) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);                     // fail fast if host is down
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));    // generous: LLM generation is slow
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
        } catch (ResourceAccessException e) {          // I/O: connect refused, DNS
            throw new LlmUnavailableException(providerName() + " unreachable: " + e.getMessage(), e);
        } catch (RestClientResponseException e) {      // non-2xx from the provider
            throw new RuntimeException(providerName() + " returned " + e.getStatusCode()
                    + ": " + truncate(e.getResponseBodyAsString()), e);
        } catch (RestClientException e) {              // e.g. read timeout while extracting the response
            if (hasCause(e, SocketTimeoutException.class)) {
                // The whole environment is too slow for the configured read timeout;
                // stop the batch and let the analysis finish static-only rather than
                // eating one long timeout per remaining unit.
                throw new LlmUnavailableException(providerName()
                        + " timed out (raise praxis.cortex." + providerName().toLowerCase()
                        + ".timeout-seconds for slow local models): " + e.getMessage(), e);
            }
            throw new RuntimeException(providerName() + " call failed: " + e.getMessage(), e);
        }
    }

    private static boolean hasCause(Throwable t, Class<? extends Throwable> type) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (type.isInstance(c)) return true;
        }
        return false;
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
