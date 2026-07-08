package com.praxis.identity.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.praxis.identity.api.PraxisPrincipal;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Runs once per request, before Spring Security's own auth filter. Reads the
 * "Authorization: Bearer <token>" header; if it's a valid Praxis JWT, it
 * populates the SecurityContext so downstream code (controllers, the
 * IdentityFacade) can find out who's calling. An invalid/expired/missing
 * token simply leaves the request unauthenticated — SecurityConfig's
 * `.anyRequest().authenticated()` rule is what actually rejects it with 401.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String token = resolveToken(request);

        if (token != null) {
            try {
                Claims claims = jwtService.parse(token).getPayload();
                authenticate(claims);
            } catch (JwtException | IllegalArgumentException ex) {
                log.debug("Rejecting request — invalid/expired JWT: {}", ex.getMessage());
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Prefer the standard "Authorization: Bearer <token>" header. Fall back to an
     * `?access_token=` query param ONLY for the SSE endpoint: the browser
     * EventSource API cannot set custom headers, so the token must ride on the URL
     * there. We scope the fallback to /events so tokens don't leak into other
     * request logs unnecessarily.
     */
    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        if (request.getRequestURI().endsWith("/events")) {
            return request.getParameter("access_token");
        }
        return null;
    }

    private void authenticate(Claims claims) {
        UUID userId = UUID.fromString(claims.getSubject());
        UUID tenantId = UUID.fromString(claims.get("tenantId", String.class));
        String role = claims.get("role", String.class);

        PraxisPrincipal principal = new PraxisPrincipal(userId, tenantId, role);
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));

        var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        log.debug("Authenticated request: userId={} tenantId={} role={}", userId, tenantId, role);
    }
}
