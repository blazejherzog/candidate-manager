package com.candidate_manager;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class KeycloakTestContainer extends GenericContainer<KeycloakTestContainer> {

    public static final String REALM = "cm-realm";
    private static final String IMAGE = "quay.io/keycloak/keycloak:26.8.2";
    private static final String ADMIN = "admin";
    private static final String REALM_FILE_RELATIVE_TO_CM_APP = "../docker-init-keycloak/cm-realm-realm.json";
    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST_OF_MAPS = new ParameterizedTypeReference<>() {
    };

    public KeycloakTestContainer() {
        super(DockerImageName.parse(IMAGE));
        withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", ADMIN);
        withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", ADMIN);
        withCommand("start-dev", "--import-realm");
        withCopyFileToContainer(MountableFile.forHostPath(REALM_FILE_RELATIVE_TO_CM_APP),
                "/opt/keycloak/data/import/cm-realm-realm.json");
        withExposedPorts(8080);
        waitingFor(Wait.forHttp("/realms/" + REALM).forPort(8080).withStartupTimeout(Duration.ofMinutes(3)));
    }

    public String url() {
        return "http://%s:%d".formatted(getHost(), getMappedPort(8080));
    }

    public String issuerUri() {
        return url() + "/realms/" + REALM;
    }

    public String readGeneratedBackendClientSecret() {
        String token = masterAdminToken();
        String clientUuid = String.valueOf(adminGet(token, "/admin/realms/{realm}/clients?clientId=cm-backend", REALM).get(0).get("id"));
        Map<String, Object> secret = client().get()
                .uri("/admin/realms/{realm}/clients/{id}/client-secret", REALM, clientUuid)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        return String.valueOf(Objects.requireNonNull(secret).get("value"));
    }

    public String loginThroughWebClient(String username, String password) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", "cm-web");
        form.add("username", username);
        form.add("password", password);
        return token(REALM, form);
    }

    public String backendServiceAccountTokenWithoutApiAudience() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", "cm-backend");
        form.add("client_secret", readGeneratedBackendClientSecret());
        return token(REALM, form);
    }

    public boolean userExists(String username) {
        return !adminGet(masterAdminToken(), "/admin/realms/{realm}/users?username={username}&exact=true", REALM, username).isEmpty();
    }

    public UUID userIdOf(String username) {
        return UUID.fromString(String.valueOf(adminGet(masterAdminToken(),
                "/admin/realms/{realm}/users?username={username}&exact=true", REALM, username).get(0).get("id")));
    }

    public List<String> realmRolesOf(String username) {
        String token = masterAdminToken();
        String userId = String.valueOf(adminGet(token, "/admin/realms/{realm}/users?username={username}&exact=true", REALM, username)
                .get(0).get("id"));
        return adminGet(token, "/admin/realms/{realm}/users/{id}/role-mappings/realm", REALM, userId).stream()
                .map(role -> String.valueOf(role.get("name")))
                .toList();
    }

    public void deleteRealmRole(String role) {
        client().delete()
                .uri("/admin/realms/{realm}/roles/{role}", REALM, role)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + masterAdminToken())
                .retrieve()
                .toBodilessEntity();
    }

    public void createRealmRole(String role) {
        client().post()
                .uri("/admin/realms/{realm}/roles", REALM)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + masterAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", role))
                .retrieve()
                .toBodilessEntity();
    }

    private String masterAdminToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", "admin-cli");
        form.add("username", ADMIN);
        form.add("password", ADMIN);
        return token("master", form);
    }

    private String token(String realm, MultiValueMap<String, String> form) {
        Map<String, Object> response = client().post()
                .uri("/realms/{realm}/protocol/openid-connect/token", realm)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        return String.valueOf(Objects.requireNonNull(response).get("access_token"));
    }

    private List<Map<String, Object>> adminGet(String token, String uri, Object... variables) {
        return Objects.requireNonNull(client().get()
                .uri(uri, variables)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(LIST_OF_MAPS));
    }

    private RestClient client() {
        return RestClient.create(url());
    }
}
