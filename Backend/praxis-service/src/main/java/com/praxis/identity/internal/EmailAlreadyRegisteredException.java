package com.praxis.identity.internal;

import com.praxis.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public class EmailAlreadyRegisteredException extends ApiException {
    public EmailAlreadyRegisteredException(String email) {
        super(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED",
                "An account with email '" + email + "' already exists.");
    }
}
