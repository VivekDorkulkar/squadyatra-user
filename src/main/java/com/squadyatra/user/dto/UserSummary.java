package com.squadyatra.user.dto;

import java.util.UUID;

import com.squadyatra.user.entities.UserAccount;
import com.squadyatra.user.enums.AccountStatus;
import com.squadyatra.user.enums.UserType;

public record UserSummary(UUID id, String fullName, UserType userType, AccountStatus status) {
    public static UserSummary from(UserAccount account) {
        return new UserSummary(account.getId(), account.getFullName(), account.getUserType(), account.getStatus());
    }
}
