package com.praxis.intake.internal;

import com.praxis.common.exception.ApiException;
import org.springframework.http.HttpStatus;

/**
 * 400 for uploads refused before staging: unsupported type, empty file, or
 * over the size cap. Carries a distinct code per rejection reason so the
 * client can react without parsing the message.
 */
public class InvalidUploadException extends ApiException {

    public InvalidUploadException(String code, String message) {
        super(HttpStatus.BAD_REQUEST, code, message);
    }
}
