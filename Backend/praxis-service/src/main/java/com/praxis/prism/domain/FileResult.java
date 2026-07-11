package com.praxis.prism.domain;

import com.praxis.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * One analyzed .java file: its path, aggregate metrics, and the source text
 * (persisted so the dashboard can show it after the workspace is deleted).
 */
@Entity
@Table(name = "file_result")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FileResult extends BaseEntity {

    @Id
    private UUID id;

    @Column(name = "analysis_id", nullable = false)
    private UUID analysisId;

    @Column(nullable = false)
    private String path;

    private Integer loc;
    private Integer complexity;

    @Column(name = "class_count")
    private Integer classCount;

    @Column(columnDefinition = "text")
    private String source;

    public FileResult(UUID id, UUID analysisId, String path, int loc, int complexity, int classCount, String source) {
        this.id = id;
        this.analysisId = analysisId;
        this.path = path;
        this.loc = loc;
        this.complexity = complexity;
        this.classCount = classCount;
        this.source = source;
    }
}
