package com.squadyatra.user.client;

import java.util.UUID;

import com.squadyatra.user.dto.LoginResult;

public record SupabaseSession(LoginResult tokens, UUID userId, String email, boolean emailVerified) {
}