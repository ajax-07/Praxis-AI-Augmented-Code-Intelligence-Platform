package com.praxis.chronicle.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.praxis.conductor.api.AnalysisQuery;
import com.praxis.conductor.api.dto.AnalysisSummary;
import com.praxis.identity.api.IdentityFacade;
import com.praxis.prism.api.AnalysisResultQuery;
import com.praxis.prism.api.dto.FileDetail;
import com.praxis.prism.api.dto.FileSummary;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Dashboard read endpoints. Every route is tenant-scoped: we resolve the caller
 * via IdentityFacade and refuse to serve an analysis that isn't theirs (404, so
 * we don't even confirm the id exists to a stranger).
 *
 *   GET /api/v1/analyses                       -> history list (this tenant)
 *   GET /api/v1/analyses/{id}/files            -> file list with finding counts
 *   GET /api/v1/analyses/{id}/files/{fileId}   -> source + findings for one file
 *
 * (POST /analyses, GET /analyses/{id}, and the SSE stream live in Conductor.)
 */
@RestController
@RequestMapping("/api/v1/analyses")
public class DashboardController {

    private static final Logger log = LoggerFactory.getLogger(DashboardController.class);

    private final IdentityFacade identity;
    private final AnalysisQuery analysisQuery;
    private final AnalysisResultQuery resultQuery;

    public DashboardController(IdentityFacade identity, AnalysisQuery analysisQuery,
                               AnalysisResultQuery resultQuery) {
        this.identity = identity;
        this.analysisQuery = analysisQuery;
        this.resultQuery = resultQuery;
    }

    /** History view: the tenant's analyses, newest first. */
    @GetMapping
    public List<AnalysisSummary> list() {
        UUID tenantId = identity.currentTenantId();
        log.debug("Dashboard: listing analyses for tenantId={}", tenantId);
        return analysisQuery.list(tenantId);
    }

    /** File list for one owned analysis (used to build the dashboard file tree). */
    @GetMapping("/{id}/files")
    public List<FileSummary> files(@PathVariable UUID id) {
        log.debug("Dashboard: files for analysisId={}", id);
        requireOwned(id);
        return resultQuery.files(id);
    }

    /** Source + findings for a single file within an owned analysis. */
    @GetMapping("/{id}/files/{fileId}")
    public FileDetail fileDetail(@PathVariable UUID id, @PathVariable UUID fileId) {
        log.debug("Dashboard: file detail analysisId={} fileId={}", id, fileId);
        requireOwned(id);
        return resultQuery.fileDetail(id, fileId)
                .orElseThrow(() -> new ResourceNotFoundException("FILE_NOT_FOUND", "No such file in this analysis"));
    }

    /**
     * Verifies the current tenant owns the analysis. Throws 404 (not 403) on a
     * miss so we never reveal that an id belongs to someone else.
     */
    private void requireOwned(UUID analysisId) {
        analysisQuery.find(analysisId, identity.currentTenantId())
                .orElseThrow(() -> new ResourceNotFoundException("ANALYSIS_NOT_FOUND", "No such analysis"));
    }
}
