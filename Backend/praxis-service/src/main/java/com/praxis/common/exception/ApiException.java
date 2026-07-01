package com.praxis.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base type for all deliberately-thrown, client-facing errors. Any module can
 * extend this; {@link GlobalExceptionHandler} turns it into a consistent
 * {@link com.praxis.common.dto.ApiError} response with the right HTTP status.
 */
@Getter
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    protected ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

}
