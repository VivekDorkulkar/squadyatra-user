package com.squadyatra.user.enums;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonValue;

public enum AccountStatus {
    ACTIVE, INACTIVE, SUSPENDED;

    @JsonValue
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }
}