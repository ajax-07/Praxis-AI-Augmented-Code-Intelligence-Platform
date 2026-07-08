package com.praxis.verdict.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.praxis.verdict.api.HealthScorer;
import com.praxis.verdict.api.dto.ScoringInput;
import org.springframework.stereotype.Service;

/**
 * Health = 100 minus weighted penalties, normalized by codebase size so a large
 * repo isn't punished simply for having more code. The formula is intentionally
 * transparent and versioned (managers will ask how it's computed):
 *
 *   penalty = (critical*8 + major*3 + minor*1) findings, per 1000 lines
 *           + a small penalty when average method complexity is high
 *
 * FORMULA_VERSION lets us evolve this without silently changing historical scores.
 */
@Service
public class HealthScorerImpl implements HealthScorer {

    private static final Logger log = LoggerFactory.getLogger(HealthScorerImpl.class);

    public static final String FORMULA_VERSION = "v1";

    @Override
    public int score(ScoringInput in) {
        double kloc = Math.max(1.0, in.totalLoc() / 1000.0); // avoid divide-by-zero; floor at 1 KLOC

        double findingPenalty =
                (in.criticalFindings() * 8.0 + in.majorFindings() * 3.0 + in.minorFindings() * 1.0) / kloc;

        // Average complexity above ~5 starts costing points; caps its own contribution.
        double complexityPenalty = Math.min(20.0, Math.max(0.0, (in.averageComplexity() - 5.0) * 2.0));

        int score = Math.max(0, Math.min(100, (int) Math.round(100.0 - findingPenalty - complexityPenalty)));
        log.debug("Scored analysis [{}]: loc={} avgCx={} findings(c/M/m/i)={}/{}/{}/{} -> findingPenalty={} cxPenalty={} score={}",
                FORMULA_VERSION, in.totalLoc(), in.averageComplexity(),
                in.criticalFindings(), in.majorFindings(), in.minorFindings(), in.infoFindings(),
                String.format("%.1f", findingPenalty), String.format("%.1f", complexityPenalty), score);
        return score;
    }
}
