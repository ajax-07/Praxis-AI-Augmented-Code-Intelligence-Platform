package com.praxis.identity.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Registering creates a brand-new Tenant plus its first User, who becomes
 * that tenant's ADMIN. There is no separate "create tenant" endpoint in the
 * MVP — sign-up and tenant creation are the same action.
 */
public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 128, message = "Password must be at least 8 characters") String password,
        @NotBlank @Size(min = 2, max = 100) String tenantName
) {
}
