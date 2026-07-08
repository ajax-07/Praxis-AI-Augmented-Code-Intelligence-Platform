package com.praxis.prism.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.praxis.prism.api.FindingWriter;
import com.praxis.prism.api.dto.AiFinding;
import com.praxis.prism.domain.FindingSource;
import com.praxis.prism.domain.IssueFinding;
import com.praxis.prism.domain.Severity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Persists AI findings (from Cortex) into the shared issue_finding store. */
@Service
public class FindingWriterImpl implements FindingWriter {

    private static final Logger log = LoggerFactory.getLogger(FindingWriterImpl.class);

    private final IssueFindingRepository findings;

    public FindingWriterImpl(IssueFindingRepository findings) {
        this.findings = findings;
    }

    @Override
    @Transactional
    public void addAiFindings(UUID analysisId, UUID codeUnitId, List<AiFinding> aiFindings) {
        log.debug("Persisting {} AI finding(s) for codeUnitId={} (analysisId={})",
                aiFindings.size(), codeUnitId, analysisId);
        List<IssueFinding> rows = aiFindings.stream()
                .map(a -> new IssueFinding(UUID.randomUUID(), codeUnitId, analysisId, a.type(),
                        parseSeverity(a.severity()), FindingSource.AI, a.message(), a.suggestion(),
                        a.startLine(), a.endLine()))
                .toList();
        findings.saveAll(rows);
    }

    private Severity parseSeverity(String value) {
        try {
            return Severity.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            return Severity.INFO; // never let a bad label break persistence
        }
    }
}
