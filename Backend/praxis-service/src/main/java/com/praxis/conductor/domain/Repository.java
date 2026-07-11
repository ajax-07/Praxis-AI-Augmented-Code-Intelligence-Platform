package com.praxis.conductor.domain;

import com.praxis.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import com.praxis.common.SourceType;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A code source (a GitHub URL or an uploaded zip) that can be analyzed.
 *
 * TEMPORARY OWNERSHIP: Conductor owns this entity for now because it is the
 * first module to need it. When the Intake module grows real repository
 * management (private-repo OAuth, re-fetch, history), this entity and its
 * repository should move to Intake, and Conductor should reference it only
 * by id. Kept here now to make the async slice runnable without Intake.
 */
@Entity
@Table(name = "repository")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Repository extends BaseEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false)
    private SourceType sourceType;

    @Column(name = "source_ref", nullable = false)
    private String sourceRef;

    public Repository(UUID id, UUID tenantId, String name, SourceType sourceType, String sourceRef) {
        this.id = id;
        this.tenantId = tenantId;
        this.name = name;
        this.sourceType = sourceType;
        this.sourceRef = sourceRef;
    }
}
