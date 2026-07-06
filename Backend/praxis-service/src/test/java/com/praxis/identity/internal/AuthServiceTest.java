package com.praxis.identity.internal;

import com.praxis.identity.api.dto.LoginRequest;
import com.praxis.identity.api.dto.RegisterRequest;
import com.praxis.identity.domain.Tenant;
import com.praxis.identity.domain.User;
import com.praxis.identity.domain.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests with mocked repositories — no database needed. Proves the
 * business rules (duplicate email rejected, wrong password rejected,
 * successful register/login issue a token) independently of persistence.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private TenantRepository tenantRepository;
    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(tenantRepository, userRepository, passwordEncoder, jwtService);
    }

    @Test
    void registerCreatesATenantAndAnAdminUser() {
        var request = new RegisterRequest("dev@praxis.io", "password123", "Acme Inc");
        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(passwordEncoder.encode(request.password())).thenReturn("hashed-password");
        when(jwtService.issue(any(), any(), eq("ADMIN"))).thenReturn("fake-jwt-token");

        var response = authService.register(request);

        assertThat(response.token()).isEqualTo("fake-jwt-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        verify(tenantRepository).save(any(Tenant.class));
        verify(userRepository).save(any(User.class));
    }

    @Test
    void registerRejectsAnEmailThatAlreadyExists() {
        var request = new RegisterRequest("dev@praxis.io", "password123", "Acme Inc");
        when(userRepository.existsByEmail(request.email())).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
    }

    @Test
    void loginSucceedsWithCorrectPassword() {
        User existing = new User(UUID.randomUUID(), UUID.randomUUID(), "dev@praxis.io", "hashed", UserRole.ADMIN);
        when(userRepository.findByEmail("dev@praxis.io")).thenReturn(Optional.of(existing));
        when(passwordEncoder.matches("correct", "hashed")).thenReturn(true);
        when(jwtService.issue(existing.getId(), existing.getTenantId(), "ADMIN")).thenReturn("fake-jwt-token");

        var response = authService.login(new LoginRequest("dev@praxis.io", "correct"));

        assertThat(response.token()).isEqualTo("fake-jwt-token");
    }

    @Test
    void loginRejectsAWrongPassword() {
        User existing = new User(UUID.randomUUID(), UUID.randomUUID(), "dev@praxis.io", "hashed", UserRole.ADMIN);
        when(userRepository.findByEmail("dev@praxis.io")).thenReturn(Optional.of(existing));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("dev@praxis.io", "wrong")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void loginRejectsAnUnknownEmail() {
        when(userRepository.findByEmail("ghost@praxis.io")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost@praxis.io", "whatever")))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}
