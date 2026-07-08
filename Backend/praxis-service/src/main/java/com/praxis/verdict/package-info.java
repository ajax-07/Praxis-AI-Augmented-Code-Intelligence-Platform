/**
 * Verdict module: turns an analysis's aggregate signals into a 0..100 Repository
 * Health Score. A pure function of its ScoringInput — no persistence, no other
 * modules — so it's completely deterministic and unit-testable. Depends on common.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Verdict",
    allowedDependencies = {"common"}
)
package com.praxis.verdict;
