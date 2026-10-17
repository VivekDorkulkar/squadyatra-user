package com.squadyatra.user.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class RegistrationRejectedException extends ResponseStatusException {
    public RegistrationRejectedException() {
        super(HttpStatus.CONFLICT, "Registration could not be completed. Sign in or contact support");
    }
}