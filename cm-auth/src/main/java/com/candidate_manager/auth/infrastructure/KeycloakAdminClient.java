package com.candidate_manager.auth.infrastructure;

import com.candidate_manager.auth.application.KeycloakUserCommand;
import com.candidate_manager.auth.application.KeycloakUserNotFoundException;
import com.candidate_manager.shared.config.keycloak.KeycloakProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class KeycloakAdminClient {

    private final KeycloakProperties keycloakProperties;
    private final WebClient webClient;

    public String getAdminAccessToken() {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "client_credentials");
        formData.add("client_id", keycloakProperties.getClientId());
        formData.add("client_secret", keycloakProperties.getClientSecret());

        Map<String, Object> response = webClient.post()
                .uri(keycloakProperties.getUrl() + "/realms/" + keycloakProperties.getRealm() + "/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue(formData)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
                })
                .block();

        return String.valueOf(Objects.requireNonNull(response).get("access_token"));
    }

    public void createUserWithRole(KeycloakUserCommand keycloakUserCommand, String role) throws KeycloakUserNotFoundException {
        String adminToken = getAdminAccessToken();

        webClient.post()
                .uri(keycloakProperties.getUrl() + "/admin/realms/" + keycloakProperties.getRealm() + "/users")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .bodyValue(keycloakUserCommand)
                .retrieve()
                .toBodilessEntity()
                .block();

        Map<String, Object> userData = Objects.requireNonNull(webClient.get()
                        .uri(keycloakProperties.getUrl() + "/admin/realms/" + keycloakProperties.getRealm() + "/users?username=" + keycloakUserCommand.username())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .retrieve()
                        .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {
                        })
                        .block())
                .stream()
                .findFirst()
                .orElseThrow(() -> new KeycloakUserNotFoundException("User not found in Keycloak!"));

        String userId = String.valueOf(userData.get("id"));

        Map<String, Object> roleDefiniton = webClient.get()
                .uri(keycloakProperties.getUrl() + "/admin/realms/" + keycloakProperties.getRealm() + "/roles/" + role)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
                })
                .block();

        webClient.post()
                .uri(keycloakProperties.getUrl() + "/admin/realms/" + keycloakProperties.getRealm() + "/users/" + userId + "/role-mappings/realm")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(List.of(roleDefiniton))
                .retrieve()
                .toBodilessEntity()
                .block();
    }
}
