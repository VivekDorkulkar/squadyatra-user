package com.squadyatra.user.service.imp;

import java.util.Optional;
import java.util.UUID;

import com.squadyatra.user.dto.Deactivation;
import com.squadyatra.user.dto.StatusChange;
import com.squadyatra.user.dto.TypeChange;
import com.squadyatra.user.dto.UserView;
import com.squadyatra.user.entities.UserAccount;
import com.squadyatra.user.enums.AccountStatus;
import com.squadyatra.user.enums.UserType;
import com.squadyatra.user.repository.AuditRepository;
import com.squadyatra.user.repository.IdentitySyncRepository;
import com.squadyatra.user.repository.UserRepository;
import com.squadyatra.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UserServiceImplTest {
    private final UserRepository users = mock(UserRepository.class);
    private final AuditRepository audit = mock(AuditRepository.class);
    private final IdentitySyncRepository sync = mock(IdentitySyncRepository.class);
    private final UserService service = new UserServiceImpl(users, audit, sync);
    private final UUID id = UUID.randomUUID();
    private UserAccount account;

    @BeforeEach
    void setup() {
        account = new UserAccount(id, "traveler@example.com", "Traveler");
        when(users.findById(id)).thenReturn(Optional.of(account));
        when(users.findBySupabaseUserId(id)).thenReturn(Optional.of(account));
        when(users.findForUpdate(id)).thenReturn(Optional.of(account));
        when(sync.findById(any())).thenReturn(Optional.empty());
    }

    @Test
        void gatewayIdentityIsResolvedThroughTheSupabaseMapping() {
                UUID providerId = UUID.randomUUID();
                account.completeRegistration(providerId);
                when(users.findBySupabaseUserId(providerId)).thenReturn(Optional.of(account));
                UserView result = service.me(providerId);
                assertThat(result.id()).isEqualTo(id);
                assertThat(result.supabaseUserId()).isEqualTo(providerId);
    }

    @Test
    void ownerCannotReadAnotherProfileOrListUsers() {
        assertThatThrownBy(() -> service.get(id, UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
        assertThatThrownBy(() -> service.list(id, null, null, 0, 20))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
    }

    @Test
        void inactiveUserWithExistingGatewayIdentityIsRejected() {
        account.setStatus(AccountStatus.INACTIVE);
        assertThatThrownBy(() -> service.me(id)).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
    }

    @Test
    void selfDeactivationAuditsAndQueuesIdentitySync() {
        assertThat(service.deactivate(id, new Deactivation("Taking a break", 0)).status())
                .isEqualTo(AccountStatus.INACTIVE);
        verify(audit).save(any());
        verify(sync).save(any());
    }

    @Test
    void staleUpdateAndSelfAdminDemotionAreRejected() {
        assertThatThrownBy(() -> service.deactivate(id, new Deactivation("Pause", 8)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");
        account.setUserType(UserType.ADMIN);
        assertThatThrownBy(() -> service.type(id, id, new TypeChange(UserType.CONSUMER, "Demote", 0)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");
    }

    @Test
    void adminMaySuspendAnotherAccountButConsumerCannotPromote() {
        UUID target = UUID.randomUUID();
        assertThatThrownBy(() -> service.type(id, target, new TypeChange(UserType.ADMIN, "Promote", 0)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
        account.setUserType(UserType.ADMIN);
        when(users.findForUpdate(target)).thenReturn(Optional.of(new UserAccount(target, "other@example.com", "Other")));
        assertThat(service.status(id, target, new StatusChange(AccountStatus.SUSPENDED, "Policy review", 0)).status())
                .isEqualTo(AccountStatus.SUSPENDED);
    }
}