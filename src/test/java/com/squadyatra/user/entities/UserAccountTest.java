package com.squadyatra.user.entities;

import java.util.UUID;

import com.squadyatra.user.enums.AccountStatus;
import com.squadyatra.user.enums.RegistrationStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserAccountTest {
    @Test
    void pendingUserHasLocalIdentityAndPhoneBeforeSupabaseIdentityExists() {
        UUID localId = UUID.randomUUID();
        UserAccount account = UserAccount.pending(localId, "traveler@example.com", "Traveler", "+919876543210");
        assertThat(account.getId()).isEqualTo(localId);
        assertThat(account.getSupabaseUserId()).isNull();
        assertThat(account.getPhone()).isEqualTo("+919876543210");
        assertThat(account.getRegistrationStatus()).isEqualTo(RegistrationStatus.PENDING);
        assertThat(account.getStatus()).isEqualTo(AccountStatus.INACTIVE);
        UUID providerId = UUID.randomUUID();
        account.completeRegistration(providerId);
        assertThat(account.getId()).isEqualTo(localId);
        assertThat(account.getSupabaseUserId()).isEqualTo(providerId);
        assertThat(account.getRegistrationStatus()).isEqualTo(RegistrationStatus.COMPLETED);
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void failedRegistrationCannotBecomeAnActiveUnmappedAccount() {
        UserAccount account = UserAccount.pending(UUID.randomUUID(), "traveler@example.com", "Traveler", "+919876543210");
        account.failRegistration();
        assertThat(account.getRegistrationStatus()).isEqualTo(RegistrationStatus.FAILED);
        assertThat(account.getStatus()).isEqualTo(AccountStatus.INACTIVE);
        assertThat(account.getSupabaseUserId()).isNull();
    }
}