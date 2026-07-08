package com.praxis.prism.internal;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.WhileStmt;
import org.springframework.stereotype.Component;

/**
 * Pure metric functions over AST nodes — no state, trivially unit-testable.
 */
@Component
public class MetricCalculator {

    /**
     * Cyclomatic complexity = 1 + the number of independent decision points.
     * We count each branching construct plus each boolean && / || (which each
     * add a path) and each ternary. This is the standard McCabe approximation.
     */
    public int cyclomaticComplexity(Node node) {
        int decisions = 0;
        decisions += node.findAll(IfStmt.class).size();
        decisions += node.findAll(ForStmt.class).size();
        decisions += node.findAll(ForEachStmt.class).size();
        decisions += node.findAll(WhileStmt.class).size();
        decisions += node.findAll(DoStmt.class).size();
        decisions += node.findAll(CatchClause.class).size();
        decisions += node.findAll(ConditionalExpr.class).size();
        // each non-default case label is a branch
        decisions += (int) node.findAll(SwitchEntry.class).stream()
                .filter(e -> !e.getLabels().isEmpty())
                .count();
        // short-circuit boolean operators each introduce a path
        decisions += (int) node.findAll(BinaryExpr.class).stream()
                .filter(b -> b.getOperator() == BinaryExpr.Operator.AND
                        || b.getOperator() == BinaryExpr.Operator.OR)
                .count();
        return 1 + decisions;
    }

    /** Physical line span of a node (end - begin + 1); 0 if positions are unknown. */
    public int linesOfCode(Node node) {
        return node.getRange()
                .map(r -> r.end.line - r.begin.line + 1)
                .orElse(0);
    }
}
