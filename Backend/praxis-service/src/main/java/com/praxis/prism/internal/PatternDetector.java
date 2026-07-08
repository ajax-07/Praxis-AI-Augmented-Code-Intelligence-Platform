package com.praxis.prism.internal;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.praxis.prism.config.PrismProperties;
import com.praxis.prism.domain.Severity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Detects design patterns (informational) and anti-patterns (issues) via AST
 * shape. Deliberately conservative: only patterns we can spot reliably without
 * cross-file type resolution. Factory/Observer heuristics are intentionally
 * deferred — they're noisy — and can be added later behind the same interface.
 */
@Component
public class PatternDetector {

    private final PrismProperties props;
    private final MetricCalculator metrics;

    public PatternDetector(PrismProperties props, MetricCalculator metrics) {
        this.props = props;
        this.metrics = metrics;
    }

    /** Class-level findings: recognized patterns + God Object anti-pattern. */
    public List<DetectedFinding> analyzeClass(ClassOrInterfaceDeclaration clazz) {
        List<DetectedFinding> out = new ArrayList<>();
        int start = clazz.getBegin().map(p -> p.line).orElse(0);
        int end = clazz.getEnd().map(p -> p.line).orElse(0);

        if (isSingleton(clazz)) {
            out.add(new DetectedFinding("SINGLETON", Severity.INFO,
                    "Class '" + clazz.getNameAsString() + "' implements the Singleton pattern.", start, end));
        }
        if (hasBuilder(clazz)) {
            out.add(new DetectedFinding("BUILDER", Severity.INFO,
                    "Class '" + clazz.getNameAsString() + "' provides a Builder.", start, end));
        }

        int methodCount = clazz.getMethods().size();
        int loc = metrics.linesOfCode(clazz);
        if (methodCount > props.getGodObjectMethods() || loc > props.getGodObjectLoc()) {
            out.add(new DetectedFinding("GOD_OBJECT", Severity.MAJOR,
                    "Class '" + clazz.getNameAsString() + "' is too large (" + methodCount
                            + " methods, " + loc + " lines) — it likely has too many responsibilities.",
                    start, end));
        }
        return out;
    }

    /** Method-level anti-patterns: high complexity and long method. */
    public List<DetectedFinding> analyzeMethod(MethodDeclaration method) {
        List<DetectedFinding> out = new ArrayList<>();
        int start = method.getBegin().map(p -> p.line).orElse(0);
        int end = method.getEnd().map(p -> p.line).orElse(0);

        int complexity = metrics.cyclomaticComplexity(method);
        if (complexity >= props.getHighComplexity()) {
            out.add(new DetectedFinding("HIGH_COMPLEXITY", Severity.MAJOR,
                    "Method '" + method.getNameAsString() + "' has cyclomatic complexity " + complexity
                            + " — hard to test and reason about.", start, end));
        }

        int loc = metrics.linesOfCode(method);
        if (loc > props.getLongMethodLoc()) {
            out.add(new DetectedFinding("LONG_METHOD", Severity.MINOR,
                    "Method '" + method.getNameAsString() + "' is " + loc
                            + " lines long — consider extracting smaller methods.", start, end));
        }
        return out;
    }

    // ---- pattern heuristics ----

    private boolean isSingleton(ClassOrInterfaceDeclaration clazz) {
        boolean allCtorsPrivate = !clazz.getConstructors().isEmpty()
                && clazz.getConstructors().stream().allMatch(ConstructorDeclaration::isPrivate);
        boolean hasStaticSelfField = clazz.getFields().stream().anyMatch(f ->
                f.isStatic() && f.getVariables().stream()
                        .anyMatch(v -> v.getTypeAsString().equals(clazz.getNameAsString())));
        return allCtorsPrivate && hasStaticSelfField;
    }

    private boolean hasBuilder(ClassOrInterfaceDeclaration clazz) {
        return clazz.findAll(ClassOrInterfaceDeclaration.class).stream()
                .anyMatch(nested -> nested.getNameAsString().equals("Builder")
                        && nested.getMethodsByName("build").size() > 0);
    }
}
