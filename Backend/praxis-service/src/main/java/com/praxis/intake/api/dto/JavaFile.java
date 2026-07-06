package com.praxis.intake.api.dto;

import java.nio.file.Path;

/**
 * One .java file that survived filtering, described by its location and size.
 * Content is intentionally NOT loaded here — Prism reads it lazily from
 * absolutePath when it parses, so a huge repo doesn't sit in memory.
 */
public record JavaFile(String relativePath, Path absolutePath, long sizeBytes) {
}
