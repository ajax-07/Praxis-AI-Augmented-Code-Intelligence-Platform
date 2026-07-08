package com.praxis.prism.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * A single issue on a code unit. `type` is a stable string code
 * (e.g. LONG_METHOD, GOD_OBJECT, HIGH_COMPLEXITY, AI_SUGGESTION). `source`
 * records whether Prism (STATIC) or Cortex (AI) produced it.
 */
@Entity
@Table(name = "issue_finding")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IssueFinding {

    @Id
    private UUID id;

    @Column(name = "code_unit_id")
    private UUID codeUnitId;

    @Column(name = "analysis_id", nullable = false)
    private UUID analysisId;

    @Column(nullable = false)
    private String type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Severity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FindingSource source;

    @Column(nullable = false, columnDefinition = "text")
    private String message;

    @Column(columnDefinition = "text")
    private String suggestion;

    @Column(name = "start_line")
    private Integer startLine;

    @Column(name = "end_line")
    private Integer endLine;

    public IssueFinding(UUID id, UUID codeUnitId, UUID analysisId, String type, Severity severity,
                        FindingSource source, String message, String suggestion, Integer startLine, Integer endLine) {
        this.id = id;
        this.codeUnitId = codeUnitId;
        this.analysisId = analysisId;
        this.type = type;
        this.severity = severity;
        this.source = source;
        this.message = message;
        this.suggestion = suggestion;
        this.startLine = startLine;
        this.endLine = endLine;
    }
}
