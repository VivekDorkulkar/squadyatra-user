package com.squadyatra.user.client;

import java.net.http.HttpClient;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.squadyatra.user.dto.LoginResult;
import com.squadyatra.user.exceptions.DuplicateUserException;
import com.squadyatra.user.exceptions.IdentityProviderException;
import com.squadyatra.user.exceptions.RegistrationRejectedException;
import com.squadyatra.user.properties.AuthProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

@Component
public class SupabaseClient {
    private final AuthProperties properties;
    private final RestClient client;

    public SupabaseClient(AuthProperties properties) {
        this.properties = properties;
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build());
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.client = RestClient.builder().requestFactory(factory).baseUrl(properties.issuer()).build();
    }

    public void recover(String email) {
        requireKey();
        if (properties.passwordResetRedirect() == null) {
            throw new IdentityProviderException();
        }
        try {
            client.post().uri(builder -> builder.path("/recover")
                            .queryParam("redirect_to", properties.passwordResetRedirect()).build())
                    .header("apikey", properties.serviceRoleKey())
                    .contentType(MediaType.APPLICATION_JSON).body(Map.of("email", email))
                    .retrieve().toBodilessEntity();
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().is5xxServerError()
                    || exception.getStatusCode().value() == 401 || exception.getStatusCode().value() == 403) {
                throw new IdentityProviderException();
            }
        } catch (RestClientException exception) {
            throw new IdentityProviderException();
        }
    }

    public SupabaseSession signIn(String email, String password) {
        if (properties.publishableKey() == null || properties.publishableKey().isBlank()) {
            throw new IdentityProviderException();
        }
        try {
            Map<String, Object> response = client.post().uri("/token?grant_type=password")
                    .header("apikey", properties.publishableKey()).contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("email", email, "password", password))
                    .retrieve().body(new ParameterizedTypeReference<Map<String, Object>>() { });
                if (response == null || !(response.get("access_token") instanceof String accessToken)
                    || !(response.get("refresh_token") instanceof String refreshToken)
                    || !(response.get("user") instanceof Map<?, ?> user)
                    || !(user.get("id") instanceof String userId)
                    || !(user.get("email") instanceof String userEmail)) {
                throw new IdentityProviderException();
            }
                UUID supabaseUserId;
                try {
                supabaseUserId = UUID.fromString(userId);
                } catch (IllegalArgumentException exception) {
                throw new IdentityProviderException();
                }
                boolean emailVerified = user.get("email_confirmed_at") instanceof String confirmedAt
                    && !confirmedAt.isBlank();
                return new SupabaseSession(new LoginResult(accessToken, refreshToken), supabaseUserId, userEmail,
                    emailVerified);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 400 || exception.getStatusCode().value() == 401) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
            }
            if (exception.getStatusCode().value() == 429) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Try again later");
            }
            throw new IdentityProviderException();
        } catch (RestClientException exception) {
            throw new IdentityProviderException();
        }
    }

    public URI oauthRedirect(URI authorizationUrl) {
        URI expected = URI.create(properties.issuer() + "/authorize");
        URI callback = properties.passwordResetRedirect();
        var query = UriComponentsBuilder.fromUri(authorizationUrl).build().getQueryParams();
        String provider = single(query.get("provider"));
        String encodedRedirectTo = single(query.get("redirect_to"));
        String challenge = single(query.get("code_challenge"));
        String method = single(query.get("code_challenge_method"));
        URI callbackOrigin;
        try {
            callbackOrigin = URI.create(UriUtils.decode(encodedRedirectTo, StandardCharsets.UTF_8));
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid OAuth redirect");
        }
        if (!sameEndpoint(authorizationUrl, expected) || callback == null
                || !"google".equals(provider) || challenge == null || !challenge.matches("[A-Za-z0-9_-]{32,128}")
                || method == null || !"s256".equalsIgnoreCase(method)
                || !sameOrigin(callbackOrigin, callback)
                || !(callbackOrigin.getPath().isEmpty() || "/".equals(callbackOrigin.getPath()))
                || callbackOrigin.getRawQuery() != null || callbackOrigin.getFragment() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid OAuth redirect");
        }
        return authorizationUrl;
    }

    public UUID signUp(String email, String password, String fullName, String phoneNumber, String captchaToken) {
        if (properties.publishableKey() == null || properties.publishableKey().isBlank()) {
            throw new IdentityProviderException();
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("email", email);
        payload.put("password", password);
        payload.put("data", Map.of("full_name", fullName, "phone_number", phoneNumber));
        if (captchaToken != null && !captchaToken.isBlank()) {
            payload.put("gotrue_meta_security", Map.of("captcha_token", captchaToken));
        }
        try {
                Map<String, Object> response = client.post().uri(builder -> builder.path("/signup")
                        .queryParamIfPresent("redirect_to", java.util.Optional.ofNullable(properties.emailConfirmationRedirect()))
                        .build())
                    .header("apikey", properties.publishableKey()).contentType(MediaType.APPLICATION_JSON)
                    .body(payload).retrieve().body(new ParameterizedTypeReference<Map<String, Object>>() { });
            Object candidate = response == null ? null : response.getOrDefault("user", response);
            if (!(candidate instanceof Map<?, ?> user)) {
                throw new IdentityProviderException();
            }
            if (!(user.get("identities") instanceof List<?> identities) || identities.isEmpty()) {
                throw new DuplicateUserException();
            }
            if (!(user.get("email") instanceof String returnedEmail) || !email.equalsIgnoreCase(returnedEmail)
                    || !(user.get("id") instanceof String id)) {
                throw new IdentityProviderException();
            }
            try {
                return UUID.fromString(id);
            } catch (IllegalArgumentException exception) {
                throw new IdentityProviderException();
            }
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 400 || exception.getStatusCode().value() == 422) {
                throw new RegistrationRejectedException();
            }
            throw new IdentityProviderException();
        } catch (RestClientException exception) {
            throw new IdentityProviderException();
        }
    }

    public void synchronize(UUID id, String role, boolean active) {
        requireKey();
        try {
            Map<String, Object> user = client.get().uri("/admin/users/{id}", id)
                    .header("apikey", properties.serviceRoleKey())
                    .header("Authorization", "Bearer " + properties.serviceRoleKey())
                    .retrieve().body(new ParameterizedTypeReference<Map<String, Object>>() { });
            if (user == null) {
                throw new IdentityProviderException();
            }
            Map<String, Object> metadata = new LinkedHashMap<>();
            if (user.get("app_metadata") instanceof Map<?, ?> existing) {
                existing.forEach((key, value) -> {
                    if (key instanceof String name) {
                        metadata.put(name, value);
                    }
                });
            }
            metadata.put("role", role);
            metadata.put("roles", List.of(role));
            client.put().uri("/admin/users/{id}", id)
                    .header("apikey", properties.serviceRoleKey())
                    .header("Authorization", "Bearer " + properties.serviceRoleKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("app_metadata", metadata, "ban_duration", active ? "none" : "876000h"))
                    .retrieve().toBodilessEntity();
        } catch (RestClientException exception) {
            throw new IdentityProviderException();
        }
    }

    public void updatePassword(UUID id, String password) {
        requireKey();
        try {
            client.put().uri("/admin/users/{id}", id)
                    .header("apikey", properties.serviceRoleKey())
                    .header("Authorization", "Bearer " + properties.serviceRoleKey())
                    .contentType(MediaType.APPLICATION_JSON).body(Map.of("password", password))
                    .retrieve().toBodilessEntity();
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 400 || exception.getStatusCode().value() == 422) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password does not meet requirements");
            }
            throw new IdentityProviderException();
        } catch (RestClientException exception) {
            throw new IdentityProviderException();
        }
    }

    private void requireKey() {
        if (properties.serviceRoleKey() == null || properties.serviceRoleKey().isBlank()) {
            throw new IdentityProviderException();
        }
    }

    private static String single(List<String> values) {
        return values != null && values.size() == 1 ? values.getFirst() : null;
    }

    private static boolean sameEndpoint(URI actual, URI expected) {
        return actual.getScheme() != null && actual.getHost() != null
            && actual.getScheme().equalsIgnoreCase(expected.getScheme())
                && actual.getHost().equalsIgnoreCase(expected.getHost())
                && actual.getPort() == expected.getPort()
                && expected.getPath().equals(actual.getPath())
                && actual.getUserInfo() == null && actual.getFragment() == null;
    }

    private static boolean sameOrigin(URI actual, URI expected) {
        return actual.getScheme() != null && actual.getHost() != null
            && actual.getScheme().equalsIgnoreCase(expected.getScheme())
                && actual.getHost().equalsIgnoreCase(expected.getHost())
                && actual.getPort() == expected.getPort()
                && actual.getUserInfo() == null;
    }
}