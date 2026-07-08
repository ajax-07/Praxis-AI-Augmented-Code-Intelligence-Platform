package com.praxis.prism.internal;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.praxis.prism.config.PrismProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PatternDetectorTest {

    private final PrismProperties props = new PrismProperties();
    private final PatternDetector detector = new PatternDetector(props, new MetricCalculator());

    private ClassOrInterfaceDeclaration firstClass(String src) {
        return StaticJavaParser.parse(src).findAll(ClassOrInterfaceDeclaration.class).get(0);
    }

    private MethodDeclaration firstMethod(String src) {
        return StaticJavaParser.parse(src).findAll(MethodDeclaration.class).get(0);
    }

    @Test
    void detectsSingleton() {
        var clazz = firstClass("""
                class Config {
                    private static Config instance;
                    private Config() {}
                    public static Config get() { return instance; }
                }""");
        assertThat(detector.analyzeClass(clazz))
                .extracting(DetectedFinding::type).contains("SINGLETON");
    }

    @Test
    void detectsBuilder() {
        var clazz = firstClass("""
                class Pizza {
                    static class Builder {
                        Pizza build() { return new Pizza(); }
                    }
                }""");
        assertThat(detector.analyzeClass(clazz))
                .extracting(DetectedFinding::type).contains("BUILDER");
    }

    @Test
    void flagsGodObjectWhenTooManyMethods() {
        props.setGodObjectMethods(2); // lower the bar for the test
        var clazz = firstClass("class Big { void a(){} void b(){} void c(){} }");
        assertThat(detector.analyzeClass(clazz))
                .extracting(DetectedFinding::type).contains("GOD_OBJECT");
    }

    @Test
    void flagsLongMethodAndHighComplexity() {
        props.setLongMethodLoc(2);
        props.setHighComplexity(2);
        var method = firstMethod("""
                class T {
                    void busy(int x) {
                        if (x > 0) { x++; }
                        for (int i = 0; i < x; i++) { x--; }
                    }
                }""");
        assertThat(detector.analyzeMethod(method))
                .extracting(DetectedFinding::type)
                .contains("LONG_METHOD", "HIGH_COMPLEXITY");
    }

    @Test
    void cleanClassProducesNoFindings() {
        var clazz = firstClass("class Small { int add(int a, int b){ return a + b; } }");
        assertThat(detector.analyzeClass(clazz)).isEmpty();
    }
}
