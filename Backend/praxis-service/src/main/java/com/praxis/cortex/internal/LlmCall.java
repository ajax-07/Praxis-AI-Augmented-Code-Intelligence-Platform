package com.praxis.cortex.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * One recorded model call, for cost/usage accounting. Cortex owns this now; when
 * the Ledger module grows real budgets, ownership moves there and Cortex will
 * emit an event instead of writing directly.
 */
@Entity
@Table(name = "llm_call")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LlmCall {

    @Id
    private UUID id;

    @Column(name = "analysis_id")
    private UUID analysisId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String provider;

    @Column(nullable = false)
    private String model;

    @Column(name = "task_type", nullable = false)
    private String taskType;

    @Column(name = "tokens_in", nullable = false)
    private int tokensIn;

    @Column(name = "tokens_out", nullable = false)
    private int tokensOut;

    @Column(name = "cost_cents", nullable = false)
    private int costCents;

    @Column(name = "cache_hit", nullable = false)
    private boolean cacheHit;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public LlmCall(UUID id, UUID analysisId, UUID tenantId, String provider, String model,
                   String taskType, int tokensIn, int tokensOut, int costCents, boolean cacheHit) {
        this.id = id;
        this.analysisId = analysisId;
        this.tenantId = tenantId;
        this.provider = provider;
        this.model = model;
        this.taskType = taskType;
        this.tokensIn = tokensIn;
        this.tokensOut = tokensOut;
        this.costCents = costCents;
        this.cacheHit = cacheHit;
    }
}
