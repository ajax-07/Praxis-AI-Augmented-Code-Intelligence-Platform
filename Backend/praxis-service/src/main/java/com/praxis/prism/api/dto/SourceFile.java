package com.praxis.prism.api.dto;

import java.nio.file.Path;

/** A file to analyze. Conductor adapts Intake's JavaFile into this so Prism
 *  stays decoupled from the Intake module. */
public record SourceFile(String relativePath, Path absolutePath) {}
