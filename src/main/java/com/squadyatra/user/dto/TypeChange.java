package com.squadyatra.user.dto;

import com.squadyatra.user.enums.UserType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record TypeChange(@NotNull UserType userType,
                         @NotBlank @Size(max = 300) String reason,
                         @PositiveOrZero long version) {
}
