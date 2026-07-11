package com.praxis.identity.api;

import java.util.UUID;

/**
 * The authenticated identity attached to every request after JwtAuthFilter
 * runs. Inject it into any controller with
 * {@code @AuthenticationPrincipal PraxisPrincipal principal}.
 */
public record PraxisPrincipal(UUID userId, UUID tenantId, String user, String role) {
}
