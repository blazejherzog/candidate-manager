package com.candidate_manager.auth;

import com.candidate_manager.KeycloakTestContainer;
import com.candidate_manager.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class UserRegistrationIntegrationTest {

    private static final String CANDIDATE_ENDPOINT = "/api/auth/register/candidate";
    private static final String RECRUITER_ENDPOINT = "/api/auth/register/recruiter";
    private static final String RECRUITER_ONLY_ENDPOINT = "/test/secured/recruiter";
    private static final String PASSWORD = "Secret-pass-1";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private KeycloakTestContainer keycloak;

    @Test
    void registersCandidateWithCandidateRole() throws Exception {
        String username = uniqueUsername();

        register(CANDIDATE_ENDPOINT, username).andExpect(status().isCreated());

        assertThat(keycloak.realmRolesOf(username)).contains("CANDIDATE").doesNotContain("RECRUITER");
    }

    @Test
    void registersRecruiterWithRecruiterRole() throws Exception {
        String username = uniqueUsername();

        register(RECRUITER_ENDPOINT, username).andExpect(status().isCreated());

        assertThat(keycloak.realmRolesOf(username)).contains("RECRUITER").doesNotContain("CANDIDATE");
    }

    @Test
    void ignoresRoleSentInRequestBody() throws Exception {
        String username = uniqueUsername();
        String body = """
                {"username": "%s", "email": "%s@example.com", "password": "%s",
                 "firstName": "Jan", "lastName": "Kowalski", "role": "RECRUITER"}
                """.formatted(username, username, PASSWORD);

        mockMvc.perform(post(CANDIDATE_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        assertThat(keycloak.realmRolesOf(username)).contains("CANDIDATE").doesNotContain("RECRUITER");
    }

    @Test
    void assignsRoleToExactUserWhenAnotherUsernameContainsIt() throws Exception {
        String username = uniqueUsername();
        String similarUsername = "a" + username;
        register(CANDIDATE_ENDPOINT, similarUsername).andExpect(status().isCreated());

        register(RECRUITER_ENDPOINT, username).andExpect(status().isCreated());

        assertThat(keycloak.realmRolesOf(username)).contains("RECRUITER");
        assertThat(keycloak.realmRolesOf(similarUsername)).contains("CANDIDATE").doesNotContain("RECRUITER");
    }

    @Test
    void rejectsDuplicateUsernameWithConflict() throws Exception {
        String username = uniqueUsername();
        register(CANDIDATE_ENDPOINT, username).andExpect(status().isCreated());

        mockMvc.perform(post(RECRUITER_ENDPOINT).contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(username, uniqueUsername() + "@example.com")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        assertThat(keycloak.realmRolesOf(username)).contains("CANDIDATE").doesNotContain("RECRUITER");
    }

    @Test
    void rejectsDuplicateEmailWithConflict() throws Exception {
        String username = uniqueUsername();
        register(CANDIDATE_ENDPOINT, username).andExpect(status().isCreated());
        String otherUsername = uniqueUsername();

        mockMvc.perform(post(CANDIDATE_ENDPOINT).contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(otherUsername, username + "@example.com")))
                .andExpect(status().isConflict());

        assertThat(keycloak.userExists(otherUsername)).isFalse();
    }

    @Test
    void rejectsInvalidRequestWithBadRequest() throws Exception {
        String body = """
                {"username": "%s", "email": "not-an-email", "password": "short", "firstName": "", "lastName": "Kowalski"}
                """.formatted(uniqueUsername());

        mockMvc.perform(post(CANDIDATE_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors[*].field", hasItems("email", "password", "firstName")));
    }

    @Test
    void deletesCreatedUserWhenRoleCannotBeAssigned() throws Exception {
        String username = uniqueUsername();
        keycloak.deleteRealmRole("RECRUITER");
        try {
            register(RECRUITER_ENDPOINT, username)
                    .andExpect(status().isBadGateway())
                    .andExpect(jsonPath("$.status").value(502));
        } finally {
            keycloak.createRealmRole("RECRUITER");
        }

        assertThat(keycloak.userExists(username)).isFalse();
    }

    @Test
    void rejectsRequestWithoutToken() throws Exception {
        mockMvc.perform(get(RECRUITER_ONLY_ENDPOINT)).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsTokenIssuedForAnotherAudience() throws Exception {
        String token = keycloak.backendServiceAccountTokenWithoutApiAudience();

        mockMvc.perform(get(RECRUITER_ONLY_ENDPOINT).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void forbidsEndpointForUserWithoutRequiredRole() throws Exception {
        String username = uniqueUsername();
        register(CANDIDATE_ENDPOINT, username).andExpect(status().isCreated());

        mockMvc.perform(get(RECRUITER_ONLY_ENDPOINT).header(HttpHeaders.AUTHORIZATION, bearerFor(username)))
                .andExpect(status().isForbidden());
    }

    @Test
    void mapsRealmRoleFromTokenToSpringRole() throws Exception {
        String username = uniqueUsername();
        register(RECRUITER_ENDPOINT, username).andExpect(status().isCreated());

        mockMvc.perform(get(RECRUITER_ONLY_ENDPOINT).header(HttpHeaders.AUTHORIZATION, bearerFor(username)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@ == 'ROLE_RECRUITER')]").exists());
    }

    private ResultActions register(String endpoint, String username) throws Exception {
        return mockMvc.perform(post(endpoint).contentType(MediaType.APPLICATION_JSON)
                .content(requestBody(username, username + "@example.com")));
    }

    private String requestBody(String username, String email) {
        return """
                {"username": "%s", "email": "%s", "password": "%s", "firstName": "Jan", "lastName": "Kowalski"}
                """.formatted(username, email, PASSWORD);
    }

    private String bearerFor(String username) {
        return "Bearer " + keycloak.loginThroughWebClient(username, PASSWORD);
    }

    private static String uniqueUsername() {
        return "user" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
