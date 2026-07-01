package com.praxis.identity.internal;

import com.praxis.identity.api.IdentityFacade;
import com.praxis.identity.api.PraxisPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class IdentityFacadeImpl implements IdentityFacade {

    @Override
    public UUID currentTenantId() {
        return principal().tenantId();
    }

    @Override
    public UUID currentUserId() {
        return principal().userId();
    }

    @Override
    public String currentRole() {
        return principal().role();
    }

    private PraxisPrincipal principal() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof PraxisPrincipal p) {
            return p;
        }
        throw new IllegalStateException(
                "No authenticated PraxisPrincipal in the current security context. " +
                "IdentityFacade must only be called from within an authenticated request.");
    }
}
