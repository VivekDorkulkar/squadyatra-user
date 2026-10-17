package com.squadyatra.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordReset(@NotBlank @Email @Size(max = 320) String email) {
}
