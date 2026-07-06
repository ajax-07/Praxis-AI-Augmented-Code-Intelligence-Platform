package com.praxis.conductor.internal;

import com.praxis.common.exception.ApiException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public class AnalysisNotFoundException extends ApiException {
    public AnalysisNotFoundException(UUID id) {
        super(HttpStatus.NOT_FOUND, "ANALYSIS_NOT_FOUND",
                "No analysis found with id '" + id + "' for this tenant.");
    }
}
