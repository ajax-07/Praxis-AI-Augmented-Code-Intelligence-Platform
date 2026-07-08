package com.praxis.identity.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.praxis.identity.api.dto.AuthResponse;
import com.praxis.identity.api.dto.LoginRequest;
import com.praxis.identity.api.dto.RegisterRequest;
import com.praxis.identity.domain.Tenant;
import com.praxis.identity.domain.User;
import com.praxis.identity.domain.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Owns the two things that create a JWT: registration (which also creates
 * the tenant) and login. Kept deliberately small — no password reset, no
 * email verification, no OAuth. Those are Phase 2.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    /**
     * Creates a new Tenant and its first User (always ADMIN of that tenant),
     * then returns a ready-to-use JWT. One HTTP call = sign-up complete.
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        log.info("Registration requested: tenantName='{}', email={}", request.tenantName(), request.email());
        if (userRepository.existsByEmail(request.email())) {
            log.warn("Registration rejected — email already registered: {}", request.email());
            throw new EmailAlreadyRegisteredException(request.email());
        }

        Tenant tenant = new Tenant(UUID.randomUUID(), request.tenantName());
        tenantRepository.save(tenant);

        User user = new User(
                UUID.randomUUID(),
                tenant.getId(),
                request.email(),
                passwordEncoder.encode(request.password()),
                UserRole.ADMIN
        );
        userRepository.save(user);

        String token = jwtService.issue(user.getId(), tenant.getId(), user.getRole().name());
        log.info("Registration complete: userId={} is ADMIN of new tenantId={}", user.getId(), tenant.getId());
        return new AuthResponse(token);
    }

    public AuthResponse login(LoginRequest request) {
        log.debug("Login attempt for email={}", request.email());
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> {
                    log.warn("Login failed — no account for email={}", request.email());
                    return new InvalidCredentialsException();
                });

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            log.warn("Login failed — bad password for userId={}", user.getId());
            throw new InvalidCredentialsException();
        }

        String token = jwtService.issue(user.getId(), user.getTenantId(), user.getRole().name());
        log.info("Login success: userId={} tenantId={}", user.getId(), user.getTenantId());
        return new AuthResponse(token);
    }
}
