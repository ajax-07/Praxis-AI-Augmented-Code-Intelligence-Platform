package com.praxis.identity.web;

import com.praxis.identity.api.dto.AuthResponse;
import com.praxis.identity.api.dto.LoginRequest;
import com.praxis.identity.api.dto.RegisterRequest;
import com.praxis.identity.api.dto.TokenRefreshRequest;
import com.praxis.identity.internal.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The only two public (unauthenticated) endpoints in the whole app —
 * everything else requires the JWT these return. See SecurityConfig for
 * the permitAll() rule that allows this controller's paths through.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody TokenRefreshRequest request){
        return ResponseEntity.ok(authService.refresh(request));
    }
}
