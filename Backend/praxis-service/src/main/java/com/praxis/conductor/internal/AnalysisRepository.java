package com.praxis.conductor.internal;

import com.praxis.conductor.domain.Analysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnalysisRepository extends JpaRepository<Analysis, UUID> {

    /** Tenant-scoped lookup — the only way the read side fetches an analysis. */
    Optional<Analysis> findByIdAndTenantId(UUID id, UUID tenantId);

    /** History view: a tenant's analyses, newest first. */
    List<Analysis> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
