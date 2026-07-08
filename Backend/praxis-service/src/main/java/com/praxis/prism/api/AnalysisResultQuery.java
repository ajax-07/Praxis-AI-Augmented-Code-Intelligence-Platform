package com.praxis.prism.api;

import com.praxis.prism.api.dto.FileDetail;
import com.praxis.prism.api.dto.FileSummary;
import com.praxis.prism.api.dto.SeverityCounts;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Read side used by Chronicle (dashboard) and the pipeline (scoring input). */
public interface AnalysisResultQuery {
    List<FileSummary> files(UUID analysisId);
    Optional<FileDetail> fileDetail(UUID analysisId, UUID fileResultId);
    SeverityCounts severityCounts(UUID analysisId);
}
