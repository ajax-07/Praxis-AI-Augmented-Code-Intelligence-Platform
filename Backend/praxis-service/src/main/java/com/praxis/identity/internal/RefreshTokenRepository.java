package com.praxis.identity.internal;

import com.praxis.identity.domain.RefreshToken;
import com.praxis.identity.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    /** Look up a presented refresh token — absence means unknown/revoked/rotated-away. */
    Optional<RefreshToken> findByToken(String token);

    /** Clears any existing refresh tokens for a user (one active token per user). */
    void deleteByUser(User user);
}
