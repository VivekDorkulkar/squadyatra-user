package com.squadyatra.user.service.imp;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import com.squadyatra.user.client.SupabaseClient;
import com.squadyatra.user.service.PasswordResetService;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class PasswordResetServiceImplTest {
    @Test
    void normalizesEmailAndThrottlesRepeatedRecovery() {
        SupabaseClient provider = mock(SupabaseClient.class);
        PasswordResetService service = new PasswordResetServiceImpl(provider,
                Clock.fixed(Instant.parse("2026-10-18T00:00:00Z"), ZoneOffset.UTC));
        for (int request = 0; request < 3; request++) {
            service.request("Traveler@Example.com", "127.0.0.1");
        }
        verify(provider, times(3)).recover("traveler@example.com");
        assertThatThrownBy(() -> service.request("TRAVELER@example.com", "127.0.0.2"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("429")
            .hasMessageContaining("wait 15 minutes");
    }
}