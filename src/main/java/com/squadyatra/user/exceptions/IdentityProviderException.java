package com.squadyatra.user.exceptions;

public class IdentityProviderException extends RuntimeException {
    public IdentityProviderException() {
        super("Identity provider is temporarily unavailable");
    }
}