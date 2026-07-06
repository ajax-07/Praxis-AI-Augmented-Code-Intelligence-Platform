package com.praxis.identity.internal;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure unit test — no Spring context, no database. JwtService has no
 * dependencies of its own, so it's instantiated directly with a test secret.
 */
class JwtServiceTest {

    private final JwtService jwtService =
            new JwtService("test-secret-must-be-at-least-32-characters-long", 60);

    @Test
    void issuesATokenAndParsesTheSameClaimsBackOut() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        String token = jwtService.issue(userId, tenantId, "ADMIN");
        Claims claims = jwtService.parse(token).getPayload();

        assertThat(claims.getSubject()).isEqualTo(userId.toString());
        assertThat(claims.get("tenantId", String.class)).isEqualTo(tenantId.toString());
        assertThat(claims.get("role", String.class)).isEqualTo("ADMIN");
    }

    @Test
    void rejectsATamperedToken() {
        String token = jwtService.issue(UUID.randomUUID(), UUID.randomUUID(), "MEMBER");
        String tampered = token.substring(0, token.length() - 4) + "abcd";

        assertThatThrownBy(() -> jwtService.parse(tampered))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsSecretsShorterThan32Bytes() {
        assertThatThrownBy(() -> new JwtService("too-short", 60))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
