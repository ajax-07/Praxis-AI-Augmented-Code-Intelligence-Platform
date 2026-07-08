package com.praxis.prism.internal;

import com.praxis.prism.api.AnalysisResultQuery;
import com.praxis.prism.api.dto.FileDetail;
import com.praxis.prism.api.dto.FileSummary;
import com.praxis.prism.api.dto.FindingView;
import com.praxis.prism.api.dto.SeverityCounts;
import com.praxis.prism.domain.CodeUnit;
import com.praxis.prism.domain.Severity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Read side for the dashboard and scoring. A finding belongs to a file through
 * its code unit (finding -> code_unit.file_result_id -> file), so attribution is
 * an explicit join, never a line-number guess.
 */
@Service
@Transactional(readOnly = true)
public class AnalysisResultQueryImpl implements AnalysisResultQuery {

    private final FileResultRepository fileResults;
    private final CodeUnitRepository codeUnits;
    private final IssueFindingRepository findings;

    public AnalysisResultQueryImpl(FileResultRepository fileResults, CodeUnitRepository codeUnits,
                                   IssueFindingRepository findings) {
        this.fileResults = fileResults;
        this.codeUnits = codeUnits;
        this.findings = findings;
    }

    @Override
    public List<FileSummary> files(UUID analysisId) {
        // unitId -> fileResultId, then count findings per file in a single pass.
        Map<UUID, UUID> unitToFile = codeUnits.findByAnalysisId(analysisId).stream()
                .collect(Collectors.toMap(CodeUnit::getId, CodeUnit::getFileResultId));
        Map<UUID, Long> countByFile = findings.findByAnalysisId(analysisId).stream()
                .map(f -> unitToFile.get(f.getCodeUnitId()))
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        return fileResults.findByAnalysisIdOrderByPath(analysisId).stream()
                .map(f -> new FileSummary(f.getId(), f.getPath(), f.getLoc(), f.getComplexity(),
                        f.getClassCount(), countByFile.getOrDefault(f.getId(), 0L)))
                .toList();
    }

    @Override
    public Optional<FileDetail> fileDetail(UUID analysisId, UUID fileResultId) {
        return fileResults.findByIdAndAnalysisId(fileResultId, analysisId).map(file -> {
            List<UUID> unitIds = codeUnits.findByFileResultId(fileResultId).stream()
                    .map(CodeUnit::getId).toList();
            List<FindingView> views = unitIds.isEmpty() ? List.of()
                    : findings.findByCodeUnitIdIn(unitIds).stream()
                    .map(fd -> new FindingView(fd.getType(), fd.getSeverity().name(), fd.getSource().name(),
                            fd.getMessage(), fd.getSuggestion(), fd.getStartLine(), fd.getEndLine()))
                    .toList();
            return new FileDetail(file.getPath(), file.getSource(), views);
        });
    }

    @Override
    public SeverityCounts severityCounts(UUID analysisId) {
        return new SeverityCounts(
                findings.countByAnalysisIdAndSeverity(analysisId, Severity.CRITICAL),
                findings.countByAnalysisIdAndSeverity(analysisId, Severity.MAJOR),
                findings.countByAnalysisIdAndSeverity(analysisId, Severity.MINOR),
                findings.countByAnalysisIdAndSeverity(analysisId, Severity.INFO));
    }
}
