package com.praxis.conductor.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * One run of the pipeline over one Repository. This is the aggregate root the
 * Conductor manages. repositoryId and tenantId are raw UUID columns (not JPA
 * associations) — the same deliberate decoupling used across Praxis so
 * modules never share entity graphs.
 *
 * State transitions are the ONLY way status changes; they are guarded so an
 * analysis can never move backwards or leave a terminal state (which matters
 * when a Redis job is redelivered).
 */
@Entity
@Table(name = "analysis")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Analysis {

    @Id
    private UUID id;

    @Column(name = "repository_id", nullable = false)
    private UUID repositoryId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AnalysisStatus status;

    @Column(name = "health_score")
    private Integer healthScore;

    @Column(name = "prompt_version", nullable = false)
    private String promptVersion;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Analysis(UUID id, UUID repositoryId, UUID tenantId, String promptVersion) {
        this.id = id;
        this.repositoryId = repositoryId;
        this.tenantId = tenantId;
        this.promptVersion = promptVersion;
        this.status = AnalysisStatus.QUEUED;
    }

    /** Move to a working stage. Ignored if already terminal (redelivery-safe). */
    public void advanceTo(AnalysisStatus next) {
        if (status.isTerminal()) {
            return;
        }
        if (this.startedAt == null) {
            this.startedAt = Instant.now();
        }
        this.status = next;
    }

    public void markComplete(int healthScore) {
        if (status.isTerminal()) {
            return;
        }
        this.status = AnalysisStatus.COMPLETE;
        this.healthScore = healthScore;
        this.completedAt = Instant.now();
    }

    public void markFailed(String reason) {
        if (status.isTerminal()) {
            return;
        }
        this.status = AnalysisStatus.FAILED;
        this.errorMessage = reason;
        this.completedAt = Instant.now();
    }
}
