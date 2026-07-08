package com.praxis.conductor.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.praxis.conductor.api.AnalysisQuery;
import com.praxis.conductor.api.dto.AnalysisSummary;
import com.praxis.conductor.domain.Analysis;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class AnalysisQueryImpl implements AnalysisQuery {

    private static final Logger log = LoggerFactory.getLogger(AnalysisQueryImpl.class);

    private final AnalysisRepository analyses;

    public AnalysisQueryImpl(AnalysisRepository analyses) {
        this.analyses = analyses;
    }

    @Override
    public Optional<AnalysisSummary> find(UUID analysisId, UUID tenantId) {
        return analyses.findByIdAndTenantId(analysisId, tenantId).map(this::toSummary);
    }

    @Override
    public List<AnalysisSummary> list(UUID tenantId) {
        log.debug("Listing analyses for tenantId={}", tenantId);
        return analyses.findByTenantIdOrderByCreatedAtDesc(tenantId).stream().map(this::toSummary).toList();
    }

    private AnalysisSummary toSummary(Analysis a) {
        return new AnalysisSummary(a.getId(), a.getRepositoryId(), a.getStatus().name(),
                a.getHealthScore(), a.getCreatedAt(), a.getCompletedAt());
    }
}
