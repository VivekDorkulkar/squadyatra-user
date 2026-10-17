package com.squadyatra.user.entities;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.squadyatra.user.enums.AccountStatus;
import com.squadyatra.user.enums.RegistrationStatus;
import com.squadyatra.user.enums.UserType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "users")
public class UserAccount {
    @Id
    private UUID id;
    @Column(unique = true)
    private UUID supabaseUserId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RegistrationStatus registrationStatus = RegistrationStatus.COMPLETED;
    @Column(nullable = false, length = 320)
    private String email;
    @Column(nullable = false, length = 100)
    private String fullName;
    @Column(length = 20)
    private String phone;
    private LocalDate dateOfBirth;
    @Column(length = 80)
    private String city;
    @Column(length = 2)
    private String countryCode;
    @Column(nullable = false, length = 64)
    private String timeZone = "UTC";
    @Column(nullable = false, length = 35)
    private String locale = "en";
    @Column(length = 500)
    private String bio;
    private boolean emailNotifications = true;
    private boolean marketingConsent;
    private Instant marketingConsentUpdatedAt;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserType userType = UserType.CONSUMER;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountStatus status = AccountStatus.ACTIVE;
    @Column(length = 300)
    private String statusReason;
    @Column(nullable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    protected UserAccount() {
    }

    public UserAccount(UUID id, String email, String fullName) {
        this.id = id;
        this.supabaseUserId = id;
        this.email = email;
        this.fullName = fullName;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public static UserAccount pending(UUID id, String email, String fullName, String phone) {
        UserAccount account = new UserAccount(id, email, fullName);
        account.supabaseUserId = null;
        account.registrationStatus = RegistrationStatus.PENDING;
        account.status = AccountStatus.INACTIVE;
        account.phone = phone;
        return account;
    }

    public UUID getSupabaseUserId() {
        return supabaseUserId;
    }

    public RegistrationStatus getRegistrationStatus() {
        return registrationStatus;
    }

    public void completeRegistration(UUID supabaseUserId) {
        this.supabaseUserId = supabaseUserId;
        this.registrationStatus = RegistrationStatus.COMPLETED;
        this.status = AccountStatus.ACTIVE;
        this.updatedAt = Instant.now();
    }

    public void failRegistration() {
        this.registrationStatus = RegistrationStatus.FAILED;
        this.updatedAt = Instant.now();
    }

    public void retryRegistration(String fullName, String phone) {
        if (registrationStatus != RegistrationStatus.FAILED || supabaseUserId != null) {
            throw new IllegalStateException("Only a failed unmapped registration can be retried");
        }
        this.fullName = fullName;
        this.phone = phone;
        this.registrationStatus = RegistrationStatus.PENDING;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public void setTimeZone(String timeZone) {
        this.timeZone = timeZone;
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public boolean isEmailNotifications() {
        return emailNotifications;
    }

    public void setEmailNotifications(boolean emailNotifications) {
        this.emailNotifications = emailNotifications;
    }

    public boolean isMarketingConsent() {
        return marketingConsent;
    }

    public void setMarketingConsent(boolean marketingConsent) {
        this.marketingConsent = marketingConsent;
    }

    public Instant getMarketingConsentUpdatedAt() {
        return marketingConsentUpdatedAt;
    }

    public void setMarketingConsentUpdatedAt(Instant marketingConsentUpdatedAt) {
        this.marketingConsentUpdatedAt = marketingConsentUpdatedAt;
    }

    public UserType getUserType() {
        return userType;
    }

    public void setUserType(UserType userType) {
        this.userType = userType;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public void setStatus(AccountStatus status) {
        this.status = status;
    }

    public String getStatusReason() {
        return statusReason;
    }

    public void setStatusReason(String statusReason) {
        this.statusReason = statusReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
