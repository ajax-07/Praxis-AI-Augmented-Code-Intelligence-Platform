package com.praxis.common;

/**
 * Where a codebase comes from. A shared-kernel value type: both Conductor
 * (which records a Repository) and Intake (which fetches one) speak it, so it
 * lives in the OPEN common module rather than in either module's internals.
 */
public enum SourceType {
    GITHUB, ZIP
}
