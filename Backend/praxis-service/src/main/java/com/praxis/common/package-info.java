/**
 * Shared kernel: cross-cutting types (errors, DTOs) that every module may use.
 * Marked OPEN so other Application Modules can reference it freely — this is
 * the one deliberate exception to Spring Modulith's default module isolation.
 */
@org.springframework.modulith.ApplicationModule(
    type = org.springframework.modulith.ApplicationModule.Type.OPEN,
    displayName = "Common"
)
package com.praxis.common;
