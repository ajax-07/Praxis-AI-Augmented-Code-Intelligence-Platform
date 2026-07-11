package com.praxis.identity.internal;

import com.praxis.identity.domain.RefreshToken;
import com.praxis.identity.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

/**
 * Signs and verifies the JWTs that carry a user's identity between requests.
 * Stateless on purpose: the token itself IS the session, so no server-side
 * session store is needed, which is what lets the backend scale horizontally.
 */
@Component
public class JwtService {

    private final SecretKey key;
    private final long jwtExpiration;
    private final long refreshExpiration;
    private RefreshTokenRepository refreshTokenRepository;

    public JwtService(
            @Value("${praxis.jwt.secret}") String secret,
            @Value("${praxis.jwt.jwtExpiration}") long jwtExpiration,
            @Value("${praxis.jwt.refreshExpiration}") long refreshExpiration,
            RefreshTokenRepository refreshTokenRepository
    ) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException(
                    "praxis.jwt.secret must be at least 32 bytes (256 bits) for HMAC-SHA signing.");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.jwtExpiration = jwtExpiration;
        this.refreshExpiration = refreshExpiration;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    public String generateAccessToken(User user){
        return issue(user, jwtExpiration);
    }

    public String generateRefreshToken(User user){
        // One active refresh token per user: issuing a new one (login, register,
        // or rotation on refresh) invalidates any previous token for this user.
        refreshTokenRepository.deleteByUser(user);
        String refreshToken = issue(user, refreshExpiration);
        refreshTokenRepository.save(
                RefreshToken.builder()
                        .token(refreshToken)
                        .expiration((Instant.now().plus(refreshExpiration, ChronoUnit.MINUTES)))
                        .user(user).build()
        );

        return refreshToken;
    }

    public String issue(User user, long expiration) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("tenantId", user.getTenantId().toString())
                .claim("role", user.getRole())
                .claim("user", user.getEmail())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration, ChronoUnit.MINUTES)))
                .signWith(key)
                .compact();
    }

    /**
     * @throws io.jsonwebtoken.JwtException if the token is malformed, expired, or has a bad signature
     */
    public Jws<Claims> parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
    }
}
