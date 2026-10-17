package com.squadyatra.user;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiContractTest {
    @Test
    void contractParsesAndIncludesTheImplementedRoutes() throws IOException {
        try (var source = Files.newInputStream(Path.of("contracts/openapi/users-v1.yaml"))) {
            Map<String, Object> contract = new Yaml().load(source);
            assertThat(contract.get("openapi")).isEqualTo("3.0.3");
            Map<?, ?> paths = (Map<?, ?>) contract.get("paths");
            assertThat(paths.keySet().stream().map(Object::toString)).containsExactlyInAnyOrder(
                    "/users", "/users/me", "/users/me/deactivate", "/users/{id}",
                    "/users/{id}/status", "/users/{id}/type", "/users/{id}/identity-sync", "/users/password-reset");
                    Map<?, ?> registration = (Map<?, ?>) ((Map<?, ?>) paths.get("/users")).get("post");
                    assertThat((List<?>) registration.get("security")).isEmpty();
                    Map<?, ?> schemas = (Map<?, ?>) ((Map<?, ?>) contract.get("components")).get("schemas");
                    Map<?, ?> request = (Map<?, ?>) schemas.get("Registration");
                    assertThat(((List<?>) request.get("required")).stream().map(Object::toString))
                        .containsExactlyInAnyOrder("fullName", "email", "phoneNumber", "password");
        }
    }
}