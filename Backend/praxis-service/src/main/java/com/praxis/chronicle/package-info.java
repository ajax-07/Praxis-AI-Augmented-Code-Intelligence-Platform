/**
 * Chronicle module: the read/presentation layer for the dashboard. It owns no
 * data — it's a thin aggregator that joins other modules' read APIs into the
 * shapes the UI wants:
 *   - conductor.api.AnalysisQuery  -> ownership check + status/score/history
 *   - prism.api.AnalysisResultQuery -> files, source, and findings
 *   - identity.api.IdentityFacade   -> the calling tenant
 *
 * Keeping reads here means Conductor stays focused on orchestration and Prism on
 * analysis, while the browser talks to one coherent dashboard surface.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Chronicle",
    allowedDependencies = {"identity :: api", "conductor :: api", "prism :: api", "common"}
)
package com.praxis.chronicle;
