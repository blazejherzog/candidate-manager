package com.candidate_manager.auth.infrastructure;

import com.candidate_manager.auth.application.IdentityProviderException;
import com.candidate_manager.auth.application.RegisterUserCommand;
import com.candidate_manager.auth.application.UserAlreadyExistsException;
import com.candidate_manager.auth.domain.UserRole;
import com.candidate_manager.shared.config.keycloak.KeycloakProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Component
public class KeycloakAdminClient {

    private final KeycloakProperties keycloakProperties;
    private final RestClient restClient;

    public KeycloakAdminClient(KeycloakProperties keycloakProperties, RestClient.Builder restClientBuilder) {
        this.keycloakProperties = keycloakProperties;
        this.restClient = restClientBuilder.baseUrl(keycloakProperties.getUrl()).build();
    }

    public String createUser(RegisterUserCommand command) {
        UserRepresentation user = new UserRepresentation(
                command.username(),
                command.email(),
                command.firstName(),
                command.lastName(),
                true,
                List.of(new CredentialRepresentation("password", command.password(), false)));
        try {
            ResponseEntity<Void> response = restClient.post()
                    .uri("/admin/realms/{realm}/users", realm())
                    .header(HttpHeaders.AUTHORIZATION, bearer(adminAccessToken()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(user)
                    .retrieve()
                    .toBodilessEntity();
            return userIdFrom(response.getHeaders().getLocation());
        } catch (HttpClientErrorException.Conflict e) {
            throw new UserAlreadyExistsException(e);
        } catch (RestClientException e) {
            log.error("Keycloak: failed to create user {}", command.username(), e);
            throw new IdentityProviderException(e);
        }
    }

    public void assignRealmRole(String userId, UserRole role) {
        try {
            String token = adminAccessToken();
            Map<String, Object> roleRepresentation = restClient.get()
                    .uri("/admin/realms/{realm}/roles/{role}", realm(), role.name())
                    .header(HttpHeaders.AUTHORIZATION, bearer(token))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });

            restClient.post()
                    .uri("/admin/realms/{realm}/users/{id}/role-mappings/realm", realm(), userId)
                    .header(HttpHeaders.AUTHORIZATION, bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(List.of(Objects.requireNonNull(roleRepresentation)))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.error("Keycloak: failed to assign role {} to user {}", role, userId, e);
            throw new IdentityProviderException(e);
        }
    }

    public void deleteUser(String userId) {
        try {
            restClient.delete()
                    .uri("/admin/realms/{realm}/users/{id}", realm(), userId)
                    .header(HttpHeaders.AUTHORIZATION, bearer(adminAccessToken()))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.error("Keycloak: failed to delete user {}", userId, e);
            throw new IdentityProviderException(e);
        }
    }

    private String adminAccessToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", keycloakProperties.getClientId());
        form.add("client_secret", keycloakProperties.getClientSecret());

        TokenResponse response = restClient.post()
                .uri("/realms/{realm}/protocol/openid-connect/token", realm())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(TokenResponse.class);
        return Objects.requireNonNull(response).accessToken();
    }

    private static String userIdFrom(URI location) {
        if (location == null) {
            throw new IllegalStateException("Keycloak did not return the Location of the created user");
        }
        String path = location.getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private String realm() {
        return keycloakProperties.getRealm();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private record UserRepresentation(
            String username,
            String email,
            String firstName,
            String lastName,
            boolean enabled,
            List<CredentialRepresentation> credentials) {
    }

    private record CredentialRepresentation(String type, String value, boolean temporary) {

        @Override
        public String toString() {
            return "CredentialRepresentation[type=%s, value=***, temporary=%s]".formatted(type, temporary);
        }
    }

    private record TokenResponse(@JsonProperty("access_token") String accessToken) {
    }
}
