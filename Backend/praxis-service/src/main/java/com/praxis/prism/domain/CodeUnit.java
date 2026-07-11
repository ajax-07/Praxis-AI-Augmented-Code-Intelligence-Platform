package com.praxis.prism.domain;

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

import java.util.UUID;

/**
 * A class or method within a file. risk_score (0..100) is what the Conductor
 * funnel filters on to decide which units are worth sending to the LLM.
 */
@Entity
@Table(name = "code_unit")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CodeUnit extends BaseEntity {

    @Id
    private UUID id;

    @Column(name = "analysis_id", nullable = false)
    private UUID analysisId;

    @Column(name = "file_result_id", nullable = false)
    private UUID fileResultId;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit_type", nullable = false)
    private UnitType unitType;

    @Column(nullable = false)
    private String name;

    @Column(name = "start_line")
    private Integer startLine;

    @Column(name = "end_line")
    private Integer endLine;

    @Column(name = "source_hash", nullable = false)
    private String sourceHash;

    @Column(name = "risk_score")
    private Integer riskScore;

    public CodeUnit(UUID id, UUID analysisId, UUID fileResultId, UnitType unitType, String name,
                    int startLine, int endLine, String sourceHash, int riskScore) {
        this.id = id;
        this.analysisId = analysisId;
        this.fileResultId = fileResultId;
        this.unitType = unitType;
        this.name = name;
        this.startLine = startLine;
        this.endLine = endLine;
        this.sourceHash = sourceHash;
        this.riskScore = riskScore;
    }
}
