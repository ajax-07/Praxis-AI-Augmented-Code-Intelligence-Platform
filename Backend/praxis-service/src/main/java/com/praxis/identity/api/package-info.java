/**
 * Identity's sanctioned cross-module surface. Declared as a Spring Modulith
 * Named Interface so OTHER modules (Conductor, Intake, ...) are allowed to
 * depend on the types in this package — and ONLY this package. Everything
 * under identity.internal / identity.domain stays private to Identity.
 */
@org.springframework.modulith.NamedInterface("api")
package com.praxis.identity.api;
