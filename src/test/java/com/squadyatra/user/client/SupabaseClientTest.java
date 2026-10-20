package com.squadyatra.user.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.squadyatra.user.exceptions.IdentityProviderException;
import com.squadyatra.user.exceptions.DuplicateUserException;
import com.squadyatra.user.exceptions.RegistrationRejectedException;
import com.squadyatra.user.properties.AuthProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SupabaseClientTest {
    private HttpServer server;
    private SupabaseClient client;
    private final List<Request> requests = new ArrayList<>();
    private int responseStatus;
    private String responseBody;

    @BeforeEach
    void setup() throws IOException {
        responseStatus = 200;
        responseBody = "{\"app_metadata\":{\"agency_id\":\"existing-agency\",\"permissions\":[\"users:read\"],\"roles\":[\"admin\"]}}";
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.add(new Request(exchange.getRequestMethod(), exchange.getRequestURI().toString(),
                    exchange.getRequestHeaders().getFirst("apikey"), exchange.getRequestHeaders().getFirst("Authorization"),
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
                byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseStatus, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        client = new SupabaseClient(new AuthProperties(URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
            "server-only-key", URI.create("https://app.example.com/reset-password"), "public-key",
            URI.create("https://app.example.com/login")));
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
        void signupUsesPublicKeyAndMapsRealIdentityWithoutTrustingClientRole() {
        UUID id = UUID.randomUUID();
        responseBody = "{\"user\":{\"id\":\"" + id + "\",\"email\":\"traveler@example.com\",\"identities\":[{\"provider\":\"email\"}]}}";
        assertThat(client.signUp("traveler@example.com", "a-long-test-password", "Traveler", "+919876543210", "captcha"))
            .isEqualTo(id);
        Request request = requests.getFirst();
        assertThat(request.uri()).startsWith("/auth/v1/signup?redirect_to=");
        assertThat(request.uri()).contains("redirect_to=https://app.example.com/login");
        assertThat(request.key()).isEqualTo("public-key");
        assertThat(request.authorization()).isNull();
        var body = JsonMapper.builder().build().readTree(request.body());
        assertThat(body.get("data").get("phone_number").asText()).isEqualTo("+919876543210");
        assertThat(body.get("gotrue_meta_security").get("captcha_token").asText()).isEqualTo("captcha");
        assertThat(body.has("app_metadata")).isFalse();
        }

        @Test
        void obfuscatedExistingSupabaseUserCannotBeMappedToNewLocalRegistration() {
        responseBody = "{\"user\":{\"id\":\"" + UUID.randomUUID() + "\",\"email\":\"traveler@example.com\",\"identities\":[]}}";
        assertThatThrownBy(() -> client.signUp("traveler@example.com", "a-long-test-password", "Traveler", "+919876543210", null))
            .isInstanceOf(DuplicateUserException.class);
        }

        @Test
        void signupRejectionIsDistinguishedFromUncertainProviderOutage() {
        responseStatus = 422;
        assertThatThrownBy(() -> client.signUp("traveler@example.com", "a-long-test-password", "Traveler", "+919876543210", null))
            .isInstanceOf(RegistrationRejectedException.class);
        responseStatus = 503;
        assertThatThrownBy(() -> client.signUp("traveler@example.com", "a-long-test-password", "Traveler", "+919876543210", null))
            .isInstanceOf(IdentityProviderException.class);
        }

        @Test
    void recoveryUsesConfiguredRedirectAndServerApiKey() {
        client.recover("traveler@example.com");
        assertThat(requests).hasSize(1);
        Request request = requests.getFirst();
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.uri()).startsWith("/auth/v1/recover?redirect_to=");
        assertThat(request.key()).isEqualTo("server-only-key");
        assertThat(request.body()).contains("traveler@example.com");
    }

    @Test
    void signInUsesPublicKeyAndReturnsOnlySessionTokens() {
        UUID userId = UUID.randomUUID();
        responseBody = "{\"access_token\":\"access\",\"refresh_token\":\"refresh\",\"user\":{\"id\":\""
            + userId + "\",\"email\":\"traveler@example.com\",\"email_confirmed_at\":\"2026-10-19T00:00:00Z\"}}";
        var result = client.signIn("traveler@example.com", "correct-password");
        assertThat(result.tokens().accessToken()).isEqualTo("access");
        assertThat(result.tokens().refreshToken()).isEqualTo("refresh");
        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.email()).isEqualTo("traveler@example.com");
        assertThat(result.emailVerified()).isTrue();
        Request request = requests.getFirst();
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.uri()).isEqualTo("/auth/v1/token?grant_type=password");
        assertThat(request.key()).isEqualTo("public-key");
        assertThat(request.authorization()).isNull();
        assertThat(request.body()).contains("traveler@example.com", "correct-password");
    }

    @Test
    void signInReportsUnconfirmedSupabaseEmail() {
        responseBody = "{\"access_token\":\"access\",\"refresh_token\":\"refresh\",\"user\":{\"id\":\""
                + UUID.randomUUID() + "\",\"email\":\"traveler@example.com\",\"email_confirmed_at\":null}}";
        assertThat(client.signIn("traveler@example.com", "correct-password").emailVerified()).isFalse();
    }

    @Test
    void oauthRedirectAllowsConfiguredGooglePkceUrlOnly() {
        URI valid = URI.create("http://127.0.0.1:" + server.getAddress().getPort()
                + "/auth/v1/authorize?provider=google&redirect_to=https%3A%2F%2Fapp.example.com"
                + "&code_challenge=" + "a".repeat(43) + "&code_challenge_method=s256");
        assertThat(client.oauthRedirect(valid)).isEqualTo(valid);
        URI otherHost = URI.create(valid.toString().replace("127.0.0.1", "attacker.example"));
        assertThatThrownBy(() -> client.oauthRedirect(otherHost)).hasMessageContaining("400");
        URI otherProvider = URI.create(valid.toString().replace("provider=google", "provider=github"));
        assertThatThrownBy(() -> client.oauthRedirect(otherProvider)).hasMessageContaining("400");
    }

    @Test
    void passwordUpdateUsesServerCredentialsForVerifiedIdentity() {
        UUID id = UUID.randomUUID();
        client.updatePassword(id, "a-new-strong-password");
        Request request = requests.getFirst();
        assertThat(request.method()).isEqualTo("PUT");
        assertThat(request.uri()).isEqualTo("/auth/v1/admin/users/" + id);
        assertThat(request.key()).isEqualTo("server-only-key");
        assertThat(request.authorization()).isEqualTo("Bearer server-only-key");
        assertThat(request.body()).contains("a-new-strong-password");
    }

    @Test
    void accountEligibilityErrorsDoNotRevealExistence() {
        responseStatus = 422;
        client.recover("unknown@example.com");
        assertThat(requests).hasSize(1);
    }

    @Test
    void providerOutageIsReportedWithoutLeakingProviderResponse() {
        responseStatus = 503;
        assertThatThrownBy(() -> client.recover("traveler@example.com"))
                .isInstanceOf(IdentityProviderException.class)
                .hasMessage("Identity provider is temporarily unavailable");
    }

    @Test
    void roleSyncPreservesUnrelatedMetadataAndReplacesOldRoles() {
        UUID id = UUID.randomUUID();
        client.synchronize(id, "consumer", false);
        assertThat(requests).hasSize(2);
        Request update = requests.get(1);
        assertThat(update.method()).isEqualTo("PUT");
        assertThat(update.uri()).isEqualTo("/auth/v1/admin/users/" + id);
        assertThat(update.authorization()).isEqualTo("Bearer server-only-key");
        var body = JsonMapper.builder().build().readTree(update.body());
        assertThat(body.get("app_metadata").get("agency_id").asText()).isEqualTo("existing-agency");
        assertThat(body.get("app_metadata").get("permissions").get(0).asText()).isEqualTo("users:read");
        assertThat(body.get("app_metadata").get("roles").get(0).asText()).isEqualTo("consumer");
        assertThat(body.get("ban_duration").asText()).isEqualTo("876000h");
    }

    private record Request(String method, String uri, String key, String authorization, String body) {
    }
}