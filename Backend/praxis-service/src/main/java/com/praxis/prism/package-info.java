/**
 * Prism module: deterministic static analysis. Parses .java files into an AST,
 * measures them, detects design patterns / anti-patterns, and computes a
 * per-unit risk score. Owns the analysis-result tables (file_result, code_unit,
 * issue_finding). No AI, no network — pure and fully unit-testable.
 *
 * Leaf-ish: depends only on the OPEN common module. Conductor/Cortex/Chronicle
 * consume it through "prism :: api".
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Prism",
    allowedDependencies = {"common"}
)
package com.praxis.prism;
