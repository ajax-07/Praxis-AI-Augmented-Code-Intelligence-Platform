package com.praxis.identity.api;

import java.util.UUID;

/**
 * The ONLY way other modules (Intake, Prism, Conductor, ...) may read
 * "who is making this request" — they must never import identity.internal
 * or identity.domain directly. Backed by the current Spring Security
 * context, so it only works inside an authenticated request.
 */
public interface IdentityFacade {

    UUID currentTenantId();

    UUID currentUserId();

    String currentRole();
}
