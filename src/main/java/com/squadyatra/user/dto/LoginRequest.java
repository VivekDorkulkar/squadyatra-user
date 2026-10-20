package com.squadyatra.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(@NotBlank @Email @Size(max = 320) String email,
                           @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) @NotBlank @Size(max = 128) String password) {
}