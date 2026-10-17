package com.squadyatra.user.enums;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonValue;

public enum UserType {
    CONSUMER, AGENCY, ADMIN;

    @JsonValue
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }
}