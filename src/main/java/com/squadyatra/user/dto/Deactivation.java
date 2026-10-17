package com.squadyatra.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record Deactivation(@NotBlank @Size(max = 300) String reason,
                           @PositiveOrZero long version) {
}
