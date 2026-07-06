package com.praxis.intake.api.dto;

import java.nio.file.Path;
import java.util.List;

/**
 * The output of a successful fetch: where the code was materialized
 * (workspacePath, an ephemeral dir) and the filtered .java files within it.
 * The caller MUST call SourceFetcher.release(result) when done so the
 * workspace is deleted — see AnalysisPipeline's finally block.
 */
public record FetchResult(Path workspacePath, List<JavaFile> files) {

    public int fileCount() {
        return files.size();
    }

    public long totalSizeBytes() {
        return files.stream().mapToLong(JavaFile::sizeBytes).sum();
    }
}
