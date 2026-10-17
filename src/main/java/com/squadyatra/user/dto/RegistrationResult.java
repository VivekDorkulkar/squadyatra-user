package com.squadyatra.user.dto;

import java.util.UUID;

public record RegistrationResult(UUID id, UUID supabaseUserId, String message) {
}