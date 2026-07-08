package com.praxis.prism.api.dto;

import java.util.List;

/** Center + right panels of the dashboard: the source and its findings. */
public record FileDetail(String path, String source, List<FindingView> findings) {}
