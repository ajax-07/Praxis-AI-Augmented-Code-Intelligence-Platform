package com.praxis.prism.internal;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.praxis.prism.api.StaticAnalyzer;
import com.praxis.prism.api.dto.AnalyzeCommand;
import com.praxis.prism.api.dto.SourceFile;
import com.praxis.prism.api.dto.StaticAnalysisResult;
import com.praxis.prism.api.dto.UnitSummary;
import com.praxis.prism.domain.CodeUnit;
import com.praxis.prism.domain.FileResult;
import com.praxis.prism.domain.FindingSource;
import com.praxis.prism.domain.IssueFinding;
import com.praxis.prism.domain.UnitType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * The PARSING stage. For each file: parse -> measure -> detect -> score, then
 * persist file_result / code_unit / STATIC issue_finding rows and return an
 * in-memory summary (with per-unit source snippets) the pipeline can funnel.
 *
 * One transaction per analyze() call: either the whole file set is recorded or
 * none is, keeping an analysis's results internally consistent.
 */
@Service
public class StaticAnalyzerImpl implements StaticAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(StaticAnalyzerImpl.class);

    private final AstParser parser;
    private final MetricCalculator metrics;
    private final PatternDetector patterns;
    private final RiskScorer riskScorer;
    private final FileResultRepository fileResults;
    private final CodeUnitRepository codeUnits;
    private final IssueFindingRepository findings;

    public StaticAnalyzerImpl(AstParser parser, MetricCalculator metrics, PatternDetector patterns,
                              RiskScorer riskScorer, FileResultRepository fileResults,
                              CodeUnitRepository codeUnits, IssueFindingRepository findings) {
        this.parser = parser;
        this.metrics = metrics;
        this.patterns = patterns;
        this.riskScorer = riskScorer;
        this.fileResults = fileResults;
        this.codeUnits = codeUnits;
        this.findings = findings;
    }

    @Override
    @Transactional
    public StaticAnalysisResult analyze(AnalyzeCommand command) {
        UUID analysisId = command.analysisId();
        log.info("Prism static analysis starting: analysisId={} files={}", analysisId, command.files().size());

        List<FileResult> fileRows = new ArrayList<>();
        List<CodeUnit> unitRows = new ArrayList<>();
        List<IssueFinding> findingRows = new ArrayList<>();
        List<UnitSummary> summaries = new ArrayList<>();

        int totalLoc = 0;
        long complexitySum = 0;
        int methodCount = 0;

        for (SourceFile file : command.files()) {
            var parsed = parser.parse(file.relativePath(), file.absolutePath());
            if (parsed.isEmpty()) {
                continue;
            }
            CompilationUnit cu = parsed.get().unit();
            String source = parsed.get().source();
            log.debug("Measuring file {}", file.relativePath());

            int fileLoc = metrics.linesOfCode(cu);
            List<ClassOrInterfaceDeclaration> classes = cu.findAll(ClassOrInterfaceDeclaration.class);
            int fileComplexity = cu.findAll(MethodDeclaration.class).stream()
                    .mapToInt(metrics::cyclomaticComplexity).sum();

            FileResult fileRow = new FileResult(UUID.randomUUID(), analysisId, file.relativePath(),
                    fileLoc, fileComplexity, classes.size(), source);
            fileRows.add(fileRow);
            totalLoc += fileLoc;

            // ---- class-level units ----
            for (ClassOrInterfaceDeclaration clazz : classes) {
                var classFindings = patterns.analyzeClass(clazz);
                int classLoc = metrics.linesOfCode(clazz);
                int risk = riskScorer.scoreClass(clazz.getMethods().size(), classLoc, classFindings);
                CodeUnit unit = newUnit(analysisId, fileRow.getId(), UnitType.CLASS,
                        clazz.getNameAsString(), clazz, risk);
                unitRows.add(unit);
                findingRows.addAll(toFindings(analysisId, unit.getId(), classFindings));
                summaries.add(toSummary(unit, clazz.toString()));
            }

            // ---- method-level units ----
            for (MethodDeclaration method : cu.findAll(MethodDeclaration.class)) {
                var methodFindings = patterns.analyzeMethod(method);
                int complexity = metrics.cyclomaticComplexity(method);
                int loc = metrics.linesOfCode(method);
                complexitySum += complexity;
                methodCount++;
                int risk = riskScorer.scoreMethod(complexity, loc, methodFindings);
                CodeUnit unit = newUnit(analysisId, fileRow.getId(), UnitType.METHOD,
                        method.getNameAsString(), method, risk);
                unitRows.add(unit);
                findingRows.addAll(toFindings(analysisId, unit.getId(), methodFindings));
                summaries.add(toSummary(unit, method.toString()));
            }
        }

        fileResults.saveAll(fileRows);
        codeUnits.saveAll(unitRows);
        findings.saveAll(findingRows);

        double avgComplexity = methodCount == 0 ? 0 : (double) complexitySum / methodCount;
        log.info("Prism analyzed {}: {} files, {} units, {} findings",
                analysisId, fileRows.size(), unitRows.size(), findingRows.size());
        return new StaticAnalysisResult(summaries, fileRows.size(), totalLoc, avgComplexity);
    }

    // ---- helpers ----

    private CodeUnit newUnit(UUID analysisId, UUID fileResultId, UnitType type,
                             String name, com.github.javaparser.ast.Node node, int risk) {
        int start = node.getBegin().map(p -> p.line).orElse(0);
        int end = node.getEnd().map(p -> p.line).orElse(0);
        return new CodeUnit(UUID.randomUUID(), analysisId, fileResultId, type, name,
                start, end, sha256(node.toString()), risk);
    }

    private UnitSummary toSummary(CodeUnit unit, String snippet) {
        return new UnitSummary(unit.getId(), unit.getFileResultId(), unit.getUnitType().name(),
                unit.getName(), unit.getStartLine(), unit.getEndLine(), unit.getRiskScore(), snippet);
    }

    private List<IssueFinding> toFindings(UUID analysisId, UUID unitId, List<DetectedFinding> detected) {
        List<IssueFinding> out = new ArrayList<>();
        for (DetectedFinding d : detected) {
            out.add(new IssueFinding(UUID.randomUUID(), unitId, analysisId, d.type(), d.severity(),
                    FindingSource.STATIC, d.message(), null, d.startLine(), d.endLine()));
        }
        return out;
    }

    private String sha256(String text) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e); // never happens on a JVM
        }
    }
}
