package com.squadyatra.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record Registration(
		@NotBlank @Size(max = 100) String fullName,
		@NotBlank @Email @Size(max = 320) String email,
		@NotBlank @Pattern(regexp = "\\+[1-9][0-9]{6,14}") String phoneNumber,
		@JsonProperty(access = JsonProperty.Access.WRITE_ONLY) @NotBlank @Size(min = 12, max = 128) String password,
		@JsonProperty(access = JsonProperty.Access.WRITE_ONLY) @Size(max = 2048) String captchaToken) {
	@Override
	public String toString() {
		return "Registration[credentials redacted]";
	}
}
