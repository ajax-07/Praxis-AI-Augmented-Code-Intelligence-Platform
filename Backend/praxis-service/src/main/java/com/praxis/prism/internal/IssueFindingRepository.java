package com.praxis.prism.internal;

import com.praxis.prism.domain.IssueFinding;
import com.praxis.prism.domain.Severity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface IssueFindingRepository extends JpaRepository<IssueFinding, UUID> {
    List<IssueFinding> findByAnalysisId(UUID analysisId);
    List<IssueFinding> findByCodeUnitIdIn(Collection<UUID> unitIds);
    long countByAnalysisIdAndSeverity(UUID analysisId, Severity severity);
}
