package com.praxis.prism.internal;

import com.praxis.prism.domain.FileResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FileResultRepository extends JpaRepository<FileResult, UUID> {
    List<FileResult> findByAnalysisIdOrderByPath(UUID analysisId);
    Optional<FileResult> findByIdAndAnalysisId(UUID id, UUID analysisId);
}
