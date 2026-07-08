package com.praxis.prism.api;

import com.praxis.prism.api.dto.AnalyzeCommand;
import com.praxis.prism.api.dto.StaticAnalysisResult;

/** PARSING stage entry point: parse + measure + detect + persist, in one call. */
public interface StaticAnalyzer {
    StaticAnalysisResult analyze(AnalyzeCommand command);
}
