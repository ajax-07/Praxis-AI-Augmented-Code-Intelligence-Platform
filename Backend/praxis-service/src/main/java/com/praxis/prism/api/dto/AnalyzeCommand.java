package com.praxis.prism.api.dto;

import java.util.List;
import java.util.UUID;

public record AnalyzeCommand(UUID analysisId, List<SourceFile> files) {}
