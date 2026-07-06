/**
 * Intake module: turns a code source (a GitHub URL or an uploaded zip) into a
 * filtered set of .java files sitting in a sandboxed, ephemeral workspace.
 *
 * It is a LEAF service — it depends on nothing but the OPEN common module.
 * allowedDependencies = {} enforces that: Intake must never reach into
 * Conductor, Prism, or anything else. Conductor calls IT, not the reverse.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Intake",
    allowedDependencies = {"common"}
)
package com.praxis.intake;
