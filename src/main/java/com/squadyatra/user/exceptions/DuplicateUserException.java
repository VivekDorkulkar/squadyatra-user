package com.squadyatra.user.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class DuplicateUserException extends ResponseStatusException {
    public DuplicateUserException() {
        super(HttpStatus.CONFLICT, "An account with this email already exists. Please sign in.");
    }
}