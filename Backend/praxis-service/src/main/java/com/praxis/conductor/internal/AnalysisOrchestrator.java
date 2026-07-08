package com.praxis.conductor.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.praxis.conductor.api.dto.AnalysisView;
import com.praxis.conductor.api.dto.StartAnalysisRequest;
import com.praxis.conductor.api.dto.StartAnalysisResponse;
import com.praxis.conductor.domain.Analysis;
import com.praxis.conductor.domain.Repository;
import com.praxis.identity.api.IdentityFacade;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

/**
 * The public brain of Conductor, called from the controller on the REQUEST
 * thread. Its job is to do the fast, transactional bit — persist the source +
 * a QUEUED analysis, put a job on the stream — and return immediately. It does
 * NOT run the pipeline; a background worker does. That split is what lets
 * `POST /analyses` answer in milliseconds with 202 while the work takes minutes.
 */
@Service
public class AnalysisOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AnalysisOrchestrator.class);

    private static final String PROMPT_VERSION = "v1";

    private final CodeRepositoryRepository repositories;
    private final AnalysisRepository analyses;
    private final JobPublisher jobPublisher;
    private final IdentityFacade identity;

    public AnalysisOrchestrator(
            CodeRepositoryRepository repositories,
            AnalysisRepository analyses,
            JobPublisher jobPublisher,
            IdentityFacade identity
    ) {
        this.repositories = repositories;
        this.analyses = analyses;
        this.jobPublisher = jobPublisher;
        this.identity = identity;
    }

    @Transactional
    public StartAnalysisResponse start(StartAnalysisRequest request) {
        UUID tenantId = identity.currentTenantId();
        log.info("Start analysis requested: tenantId={} name='{}' sourceType={} sourceRef={}",
                tenantId, request.name(), request.sourceType(), request.sourceRef());

        Repository repo = new Repository(
                UUID.randomUUID(), tenantId, request.name(), request.sourceType(), request.sourceRef());
        repositories.save(repo);

        Analysis analysis = new Analysis(UUID.randomUUID(), repo.getId(), tenantId, PROMPT_VERSION);
        analyses.save(analysis);
        log.info("Persisted repositoryId={} and analysisId={} in QUEUED (tenantId={})",
                repo.getId(), analysis.getId(), tenantId);

        // Publish the job ONLY after this transaction COMMITS. Previously the
        // publish ran inside the transaction, so the worker could read the
        // analysis row before it was committed — findById returned empty, the
        // pipeline bailed out, and the analysis was stuck at QUEUED forever.
        // afterCommit() runs post-commit, guaranteeing the row is visible.
        UUID analysisId = analysis.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                log.info("Transaction committed — enqueuing job for analysisId={}", analysisId);
                jobPublisher.publish(new AnalysisJob(analysisId, tenantId));
            }
        });

        return new StartAnalysisResponse(analysisId, analysis.getStatus());
    }

    @Transactional(readOnly = true)
    public AnalysisView get(UUID analysisId) {
        UUID tenantId = identity.currentTenantId();
        log.debug("Fetching analysisId={} for tenantId={}", analysisId, tenantId);
        Analysis analysis = analyses.findByIdAndTenantId(analysisId, tenantId)
                .orElseThrow(() -> new AnalysisNotFoundException(analysisId));
        return AnalysisView.from(analysis);
    }
}
