package com.praxis.identity.internal;

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
        if (userRepository.existsByEmail(request.email())) {
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
        return new AuthResponse(token);
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        String token = jwtService.issue(user.getId(), user.getTenantId(), user.getRole().name());
        return new AuthResponse(token);
    }
}
