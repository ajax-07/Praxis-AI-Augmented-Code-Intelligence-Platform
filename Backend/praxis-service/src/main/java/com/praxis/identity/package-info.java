/**
 * Identity module: authentication, tenants, users. CLOSED by default —
 * other modules must depend only on {@code com.praxis.identity.api}, never
 * on {@code .internal} or {@code .domain}. The Modulith verification test
 * (see ModularityTests) fails the build if that boundary is crossed.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Identity")
package com.praxis.identity;
