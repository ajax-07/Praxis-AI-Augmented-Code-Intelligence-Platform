package com.praxis.cortex.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Deterministic, offline stand-in for a real LLM. It produces a plausible,
 * repeatable review so the end-to-end pipeline (and the UI) work with zero
 * external dependencies or API keys. Active unless praxis.cortex.provider is
 * set to something other than "stub".
 *
 * When a real provider module lands, implement LlmClient against Spring AI's
 * ChatClient and switch the property — nothing else changes.
 */
@Component
@ConditionalOnProperty(name = "praxis.cortex.provider", havingValue = "stub", matchIfMissing = true)
public class StubLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(StubLlmClient.class);

    @Override
    public LlmResponse complete(String systemPrompt, String userPrompt) {
        String content = """
                This unit is flagged as higher-risk by static analysis. \
                Consider extracting nested logic into smaller, well-named private methods, \
                replacing deep conditionals with guard clauses or polymorphism, and adding \
                focused unit tests around its branches to make future changes safer.""";
        // Tokens are ~= characters / 4; a stable estimate keeps the cost ledger meaningful.
        log.debug("Stub LLM producing deterministic review ({} prompt chars)", systemPrompt.length() + userPrompt.length());
        int tokensIn = Math.max(1, (systemPrompt.length() + userPrompt.length()) / 4);
        int tokensOut = Math.max(1, content.length() / 4);
        return new LlmResponse(content, "STUB", "stub-reviewer-v1", tokensIn, tokensOut);
    }
}
