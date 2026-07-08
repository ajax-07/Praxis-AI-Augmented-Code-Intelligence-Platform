package com.praxis.prism.api;

import com.praxis.prism.api.dto.AiFinding;

import java.util.List;
import java.util.UUID;

/**
 * Lets Cortex record AI findings into the shared findings store that Prism
 * owns. (When a dedicated findings/Ledger module arrives, this seam can move;
 * for now Prism owning issue_finding keeps the dashboard read side simple.)
 */
public interface FindingWriter {
    void addAiFindings(UUID analysisId, UUID codeUnitId, List<AiFinding> findings);
}
