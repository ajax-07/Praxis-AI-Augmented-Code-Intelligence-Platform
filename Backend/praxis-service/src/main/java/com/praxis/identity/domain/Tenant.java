package com.praxis.identity.domain;



import com.praxis.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One customer organization. Every {@link User}, repository, and analysis
 * belongs to exactly one tenant — this is the row-level boundary that keeps
 * customers' data from ever mixing.
 */
@Entity
@Table(name = "tenant")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // required by JPA; use the constructor below in app code
public class Tenant extends BaseEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "code_residency", nullable = false)
    private CodeResidency codeResidency = CodeResidency.CLOUD_ALLOWED;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TenantPlan plan = TenantPlan.FREE;

    public Tenant(UUID id, String name) {
        this.id = id;
        this.name = name;
    }
}
