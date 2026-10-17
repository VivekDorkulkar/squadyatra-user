package com.squadyatra.user.client;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.squadyatra.user.exceptions.IdentityProviderException;
import com.squadyatra.user.exceptions.RegistrationRejectedException;
import com.squadyatra.user.properties.AuthProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

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
            Map<String, Object> response = client.post().uri("/signup")
                    .header("apikey", properties.publishableKey()).contentType(MediaType.APPLICATION_JSON)
                    .body(payload).retrieve().body(new ParameterizedTypeReference<Map<String, Object>>() { });
            Object candidate = response == null ? null : response.getOrDefault("user", response);
            if (!(candidate instanceof Map<?, ?> user)) {
                throw new IdentityProviderException();
            }
            if (!(user.get("identities") instanceof List<?> identities) || identities.isEmpty()) {
                throw new RegistrationRejectedException();
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

    private void requireKey() {
        if (properties.serviceRoleKey() == null || properties.serviceRoleKey().isBlank()) {
            throw new IdentityProviderException();
        }
    }
}