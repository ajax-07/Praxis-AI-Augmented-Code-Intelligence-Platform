package com.praxis.prism.internal;

import com.praxis.prism.domain.CodeUnit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CodeUnitRepository extends JpaRepository<CodeUnit, UUID> {
    List<CodeUnit> findByAnalysisId(UUID analysisId);
    List<CodeUnit> findByFileResultId(UUID fileResultId);
}
