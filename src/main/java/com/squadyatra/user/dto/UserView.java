package com.squadyatra.user.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.squadyatra.user.entities.UserAccount;
import com.squadyatra.user.enums.AccountStatus;
import com.squadyatra.user.enums.RegistrationStatus;
import com.squadyatra.user.enums.UserType;
import jakarta.validation.constraints.Email;

public record UserView(UUID id, UUID supabaseUserId, RegistrationStatus registrationStatus,
                       String email, String fullName, String phone,
                       LocalDate dateOfBirth, String city, String countryCode,
                       String timeZone, String locale, String bio,
                       boolean emailNotifications, boolean marketingConsent,
                       Instant marketingConsentUpdatedAt, UserType userType,
                       AccountStatus status, Instant createdAt, Instant updatedAt, long version) {
    public static UserView from(UserAccount account) {
        return new UserView(account.getId(), account.getSupabaseUserId(), account.getRegistrationStatus(),
            account.getEmail(), account.getFullName(), account.getPhone(),
                account.getDateOfBirth(), account.getCity(), account.getCountryCode(), account.getTimeZone(),
                account.getLocale(), account.getBio(), account.isEmailNotifications(), account.isMarketingConsent(),
                account.getMarketingConsentUpdatedAt(), account.getUserType(), account.getStatus(),
                account.getCreatedAt(), account.getUpdatedAt(), account.getVersion());
    }
}
