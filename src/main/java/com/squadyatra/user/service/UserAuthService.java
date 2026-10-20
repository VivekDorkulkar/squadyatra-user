package com.squadyatra.user.service;

import java.net.URI;
import java.util.UUID;

import com.squadyatra.user.dto.LoginRequest;
import com.squadyatra.user.dto.LoginResult;

public interface UserAuthService {
    LoginResult signIn(LoginRequest request, String remoteAddress);

    URI oauthRedirect(URI authorizationUrl);

    void updatePassword(UUID userId, String password);
}