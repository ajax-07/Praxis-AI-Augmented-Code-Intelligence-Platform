package com.praxis.common.exception;

import com.praxis.common.dto.ApiError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.UUID;
import java.util.stream.Collectors;

/**
 * One place that turns any thrown exception into the {@link ApiError} contract.
 * Every controller in every module benefits from this automatically — nobody
 * writes try/catch blocks in a @RestController.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(ApiException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(new ApiError(ex.getCode(), ex.getMessage(), newTraceId()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest()
                .body(new ApiError("VALIDATION_ERROR", message, newTraceId()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        // A blind 500 is undebuggable — always log the trace with the traceId we
        // return, so a support request ("traceId X") maps straight to a log line.
        String traceId = newTraceId();
        log.error("Unhandled exception [traceId={}]", traceId, ex);
        return ResponseEntity.internalServerError()
                .body(new ApiError("INTERNAL_ERROR", "Something went wrong. Please try again.", traceId));
    }

    private String newTraceId() {
        return UUID.randomUUID().toString();
    }
}
