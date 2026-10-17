package com.squadyatra.user.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record ProfileUpdate(
        @NotBlank @Size(max = 100) String fullName,
        @Pattern(regexp = "\\+[1-9][0-9]{6,14}") String phone,
        @Past LocalDate dateOfBirth,
        @Size(max = 80) String city,
        @Pattern(regexp = "[A-Z]{2}") String countryCode,
        @NotBlank @Size(max = 64) String timeZone,
        @NotBlank @Size(max = 35) String locale,
        @Size(max = 500) String bio,
        boolean emailNotifications,
        boolean marketingConsent,
        @PositiveOrZero long version) {
}
