package com.praxis.chronicle.web;

import com.praxis.common.exception.ApiException;
import org.springframework.http.HttpStatus;

/** 404 for dashboard reads. Used for both "not yours" and "doesn't exist" so we
 *  never leak which is which. */
public class ResourceNotFoundException extends ApiException {
    public ResourceNotFoundException(String code, String message) {
        super(HttpStatus.NOT_FOUND, code, message);
    }
}
