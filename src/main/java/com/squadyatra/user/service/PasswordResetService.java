package com.squadyatra.user.service;

public interface PasswordResetService {
    void request(String email, String remoteAddress);
}