package com.praxis.prism.internal;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MetricCalculatorTest {

    private final MetricCalculator calc = new MetricCalculator();

    private MethodDeclaration firstMethod(String source) {
        return StaticJavaParser.parse(source).findAll(MethodDeclaration.class).get(0);
    }

    @Test
    void complexityCountsEachBranchingConstruct() {
        // base(1) + if(1) + for(1) + while(1) = 4
        var m = firstMethod("""
                class T {
                    void run(int x) {
                        if (x > 0) { System.out.println(x); }
                        for (int i = 0; i < x; i++) { }
                        while (x > 0) { x--; }
                    }
                }""");
        assertThat(calc.cyclomaticComplexity(m)).isEqualTo(4);
    }

    @Test
    void complexityCountsShortCircuitBooleanOperators() {
        // base(1) + && (1) + || (1) = 3
        var m = firstMethod("class T { boolean ok(boolean a, boolean b, boolean c){ return a && b || c; } }");
        assertThat(calc.cyclomaticComplexity(m)).isEqualTo(3);
    }

    @Test
    void straightLineCodeHasComplexityOne() {
        var m = firstMethod("class T { int add(int a, int b){ return a + b; } }");
        assertThat(calc.cyclomaticComplexity(m)).isEqualTo(1);
    }

    @Test
    void linesOfCodeIsTheNodeSpan() {
        // method body spans lines 2..4 inclusive = 3 lines
        var m = firstMethod("class T {\n    int f(){\n        return 1;\n    }\n}");
        assertThat(calc.linesOfCode(m)).isEqualTo(3);
    }
}
