package com.squadyatra.user.controller;

import java.util.Map;
import java.util.UUID;

import com.squadyatra.user.client.SupabaseClient;
import com.squadyatra.user.dto.ProfileUpdate;
import com.squadyatra.user.dto.Registration;
import com.squadyatra.user.entities.UserAccount;
import com.squadyatra.user.enums.AccountStatus;
import com.squadyatra.user.enums.RegistrationStatus;
import com.squadyatra.user.enums.UserType;
import com.squadyatra.user.exceptions.IdentityProviderException;
import com.squadyatra.user.exceptions.RegistrationRejectedException;
import com.squadyatra.user.repository.AuditRepository;
import com.squadyatra.user.repository.IdentitySyncRepository;
import com.squadyatra.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserApiTest {
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired AuditRepository audit;
    @Autowired IdentitySyncRepository sync;
    @Autowired ObjectMapper json;
    @MockitoBean SupabaseClient provider;
    private MockMvc mvc;
    private UUID id;
        private String registrationEmail;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        id = UUID.randomUUID();
    }

        @AfterEach
        void cleanupRegistration() {
                if (registrationEmail != null) {
                        users.findByEmail(registrationEmail).ifPresent(account -> {
                                sync.deleteById(account.getId());
                                audit.deleteAll(audit.findAll().stream().filter(entry -> entry.getUserId().equals(account.getId())).toList());
                                users.delete(account);
                        });
                }
        }

    @Test
        void gatewayIdentityIsRequiredAndBearerTokensAreNotValidatedHere() throws Exception {
                mvc.perform(get("/users/me").header("Authorization", "Bearer forged")).andExpect(status().isBadRequest());
                create(id);
                mvc.perform(get("/users/me").header("X-User-Id", id.toString()).header("Authorization", "Bearer forged"))
                                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void publicRegistrationCommitsLocalRowBeforeCallingSupabaseThenMapsItsUuid() throws Exception {
        registrationEmail = id + "@signup.example.com";
        UUID supabaseId = UUID.randomUUID();
        when(provider.signUp(eq(registrationEmail), anyString(), anyString(), anyString(), any())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            UserAccount pending = users.findByEmail(registrationEmail).orElseThrow();
            assertThat(pending.getSupabaseUserId()).isNull();
            assertThat(pending.getRegistrationStatus()).isEqualTo(RegistrationStatus.PENDING);
            assertThat(pending.getStatus()).isEqualTo(AccountStatus.INACTIVE);
            assertThat(pending.getPhone()).isEqualTo("+919876543210");
            return supabaseId;
        });
        mvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON).content(registrationJson(registrationEmail)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.supabaseUserId").value(supabaseId.toString()))
                .andExpect(jsonPath("$.password").doesNotExist()).andExpect(jsonPath("$.accessToken").doesNotExist());
        UserAccount created = users.findByEmail(registrationEmail).orElseThrow();
        assertThat(created.getId()).isNotEqualTo(supabaseId);
        assertThat(created.getSupabaseUserId()).isEqualTo(supabaseId);
        assertThat(created.getRegistrationStatus()).isEqualTo(RegistrationStatus.COMPLETED);
        assertThat(created.getUserType()).isEqualTo(UserType.CONSUMER);
        assertThat(created.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(sync.existsById(created.getId())).isTrue();
        mvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON).content(registrationJson(registrationEmail)))
                .andExpect(status().isConflict());
        verify(provider, times(1)).signUp(eq(registrationEmail), anyString(), anyString(), anyString(), any());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void rejectedSignupRetainsFailedInactiveLocalRecord() throws Exception {
        registrationEmail = id + "@signup.example.com";
        when(provider.signUp(eq(registrationEmail), anyString(), anyString(), anyString(), any()))
                .thenThrow(new RegistrationRejectedException());
        mvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON).content(registrationJson(registrationEmail)))
                .andExpect(status().isConflict());
        UserAccount failed = users.findByEmail(registrationEmail).orElseThrow();
        assertThat(failed.getRegistrationStatus()).isEqualTo(RegistrationStatus.FAILED);
        assertThat(failed.getStatus()).isEqualTo(AccountStatus.INACTIVE);
        assertThat(failed.getSupabaseUserId()).isNull();
        assertThat(sync.existsById(failed.getId())).isFalse();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void explicitlyRejectedRegistrationCanBeRetriedWithoutCreatingAnotherLocalUser() throws Exception {
        registrationEmail = id + "@signup.example.com";
        UUID supabaseId = UUID.randomUUID();
        when(provider.signUp(eq(registrationEmail), anyString(), anyString(), anyString(), any()))
                .thenThrow(new RegistrationRejectedException()).thenReturn(supabaseId);
        mvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON).content(registrationJson(registrationEmail)))
                .andExpect(status().isConflict());
        UUID localId = users.findByEmail(registrationEmail).orElseThrow().getId();
        mvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON).content(registrationJson(registrationEmail)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(localId.toString()));
        assertThat(users.findByEmail(registrationEmail).orElseThrow().getSupabaseUserId()).isEqualTo(supabaseId);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void uncertainProviderFailureLeavesPendingRecordForReconciliationWithoutUnsafeRetry() throws Exception {
        registrationEmail = id + "@signup.example.com";
        when(provider.signUp(eq(registrationEmail), anyString(), anyString(), anyString(), any()))
                .thenThrow(new IdentityProviderException());
        mvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON).content(registrationJson(registrationEmail)))
                .andExpect(status().isServiceUnavailable());
        UserAccount pending = users.findByEmail(registrationEmail).orElseThrow();
        assertThat(pending.getRegistrationStatus()).isEqualTo(RegistrationStatus.PENDING);
        assertThat(pending.getSupabaseUserId()).isNull();
        mvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON).content(registrationJson(registrationEmail)))
                .andExpect(status().isConflict());
        verify(provider, times(1)).signUp(eq(registrationEmail), anyString(), anyString(), anyString(), any());
    }

    @Test
        void gatewayIdentityMapsToLocalRecordWhenIdsDiffer() throws Exception {
        UUID supabaseId = UUID.randomUUID();
        UserAccount account = UserAccount.pending(id, id + "@example.com", "Traveler", "+919876543210");
        account.completeRegistration(supabaseId);
        users.saveAndFlush(account);
        mvc.perform(get("/users/me").header("X-User-Id", supabaseId.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.supabaseUserId").value(supabaseId.toString()))
                .andExpect(jsonPath("$.phone").value("+919876543210"));
    }

    @Test
    void registrationPasswordsAreNotSerializedOrIncludedInDiagnosticStrings() throws Exception {
        Registration request = new Registration("Traveler", "traveler@example.com", "+919876543210",
                "a-long-test-password", "test-captcha");
        assertThat(request.toString()).doesNotContain(request.password(), request.captchaToken(), request.email());
        assertThat(json.writeValueAsString(request)).doesNotContain(request.password(), request.captchaToken());
    }

    @Test
    void registrationRequiresValidPhoneEmailAndPasswordWithoutCallingProvider() throws Exception {
        mvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Traveler\",\"email\":\"invalid\",\"phoneNumber\":\"123\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(provider);
    }

    @Test
    void invalidInputAndPaginationAreRejected() throws Exception {
        mvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\" \"}")).andExpect(status().isBadRequest());
        UserAccount admin = create(id);
        admin.setUserType(UserType.ADMIN);
        users.flush();
        mvc.perform(get("/users?size=101").header("X-User-Id", id.toString())).andExpect(status().isBadRequest());
    }

    @Test
    void profileUpdateIncrementsVersionAndRejectsStaleWrites() throws Exception {
        create(id);
        var update = new ProfileUpdate("Updated traveler", "+919876543210", null,
                "Pune", "IN", "Asia/Kolkata", "en-IN", "Enjoys hiking", true, true, 0);
        mvc.perform(put("/users/me").header("X-User-Id", id.toString()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(update)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.marketingConsentUpdatedAt").isNotEmpty());
        mvc.perform(put("/users/me").header("X-User-Id", id.toString()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(update))).andExpect(status().isConflict());
    }

    @Test
        void deactivatedAccountCannotUseExistingGatewayIdentity() throws Exception {
        create(id);
                String userId = id.toString();
                mvc.perform(post("/users/me/deactivate").header("X-User-Id", userId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Taking a break\",\"version\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("inactive"));
                mvc.perform(get("/users/me").header("X-User-Id", userId)).andExpect(status().isForbidden());
                mvc.perform(get("/users/" + id).header("X-User-Id", userId)).andExpect(status().isForbidden());
    }

    @Test
    void ownershipAndDatabaseAdminRoleAreEnforced() throws Exception {
        create(id);
        UUID other = UUID.randomUUID();
        create(other);
        mvc.perform(get("/users/" + other).header("X-User-Id", id.toString())).andExpect(status().isForbidden());
        mvc.perform(put("/users/" + other + "/type").header("X-User-Id", id.toString())
                .contentType(MediaType.APPLICATION_JSON).content("{\"userType\":\"admin\",\"reason\":\"Escalate\",\"version\":0}"))
                .andExpect(status().isForbidden());
        users.findById(id).orElseThrow().setUserType(UserType.ADMIN);
        users.flush();
        mvc.perform(put("/users/" + other + "/type").header("X-User-Id", id.toString())
                .contentType(MediaType.APPLICATION_JSON).content("{\"userType\":\"agency\",\"reason\":\"Approved onboarding\",\"version\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.userType").value("agency"));
        mvc.perform(get("/users").header("X-User-Id", id.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].email").doesNotExist());
    }

    @Test
    void passwordRecoveryIsPublicAndDoesNotReturnAccountDetails() throws Exception {
        mvc.perform(post("/users/password-reset").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"Traveler@Example.com\"}"))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.email").doesNotExist());
        verify(provider).recover("traveler@example.com");
    }

    private UserAccount create(UUID userId) {
        return users.saveAndFlush(new UserAccount(userId, userId + "@example.com", "Traveler"));
    }

        private String registrationJson(String email) throws Exception {
                return json.writeValueAsString(Map.of("fullName", "Traveler", "email", email,
                                "phoneNumber", "+919876543210", "password", "a-long-test-password"));
        }

}