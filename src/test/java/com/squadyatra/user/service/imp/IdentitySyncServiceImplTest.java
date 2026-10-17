package com.squadyatra.user.service.imp;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.squadyatra.user.client.SupabaseClient;
import com.squadyatra.user.entities.IdentitySync;
import com.squadyatra.user.entities.UserAccount;
import com.squadyatra.user.exceptions.IdentityProviderException;
import com.squadyatra.user.repository.IdentitySyncRepository;
import com.squadyatra.user.repository.UserRepository;
import com.squadyatra.user.service.IdentitySyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class IdentitySyncServiceImplTest {
    private final UserRepository users = mock(UserRepository.class);
    private final IdentitySyncRepository sync = mock(IdentitySyncRepository.class);
    private final SupabaseClient provider = mock(SupabaseClient.class);
    private final IdentitySyncService processor = new IdentitySyncServiceImpl(users, sync, provider);
    private final UUID id = UUID.randomUUID();
    private IdentitySync entry;

    @BeforeEach
    void setup() {
        entry = new IdentitySync(id);
        when(users.findForUpdate(id)).thenReturn(Optional.of(new UserAccount(id, "traveler@example.com", "Traveler")));
        when(sync.findById(id)).thenReturn(Optional.of(entry));
    }

    @Test
    void successDeletesOnlyThePendingWork() {
        processor.process(id);
        verify(provider).synchronize(id, "consumer", true);
        verify(sync).delete(entry);
    }

    @Test
    void providerSynchronizationUsesMappedIdentityNotLocalPrimaryKey() {
        UUID supabaseId = UUID.randomUUID();
        UserAccount account = UserAccount.pending(id, "traveler@example.com", "Traveler", "+919876543210");
        account.completeRegistration(supabaseId);
        when(users.findForUpdate(id)).thenReturn(Optional.of(account));
        processor.process(id);
        verify(provider).synchronize(supabaseId, "consumer", true);
        verify(provider, never()).synchronize(id, "consumer", true);
    }

    @Test
    void providerFailureRetainsWorkWithBackoff() {
        doThrow(new IdentityProviderException()).when(provider).synchronize(id, "consumer", true);
        processor.process(id);
        assertThat(entry.getAttempts()).isEqualTo(1);
        assertThat(entry.isFailed()).isFalse();
        assertThat(entry.getNextAttemptAt()).isAfter(Instant.now());
        verify(sync, never()).delete(any());
    }

    @Test
    void exhaustedRetriesRemainVisibleForOperatorRecovery() {
        entry.setAttempts(7);
        doThrow(new IdentityProviderException()).when(provider).synchronize(id, "consumer", true);
        processor.process(id);
        assertThat(entry.isFailed()).isTrue();
        verify(sync, never()).delete(any());
    }
}