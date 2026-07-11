package com.praxis.identity.config;

import com.praxis.identity.api.PraxisPrincipal;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

public class SecurityAuditorAware implements AuditorAware<String> {
    @Override
    public Optional<String> getCurrentAuditor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return Optional.of("SYSTEM"); // Fallback for system tasks or unregistered actions
        }

        // Returns the logged-in username
        if (authentication.getPrincipal() instanceof PraxisPrincipal praxisPrincipal) {
            return Optional.ofNullable(praxisPrincipal.user());
        }
        // Fallback if the principal is a standard string or unknown type
        return Optional.of(authentication.getName());
    }
}
