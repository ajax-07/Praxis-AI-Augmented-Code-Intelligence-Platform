/**
 * Conductor module: the orchestration engine. Owns the Analysis lifecycle
 * (state machine), turns a synchronous "start analysis" request into an
 * asynchronous background job, and streams live progress back to the browser.
 *
 * Depends on Identity's api (to scope every analysis to the calling tenant)
 * and Intake's api (to fetch source code during the FETCHING stage). It will
 * add prism/cortex/verdict as those stages become real. Each dependency is an
 * explicit Named Interface — the Modulith test fails the build otherwise.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Conductor",
    allowedDependencies = {"identity :: api", "intake :: api", "prism :: api", "cortex :: api", "verdict :: api", "common"}
)
package com.praxis.conductor;
