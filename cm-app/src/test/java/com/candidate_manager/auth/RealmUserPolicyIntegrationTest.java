package com.candidate_manager.auth;

import com.candidate_manager.KeycloakTestContainer;
import com.candidate_manager.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RealmUserPolicyIntegrationTest {

    private static final String PASSWORD = "Secret-pass-1";
    private static final int FAILED_LOGINS_BEFORE_LOCKOUT = 5;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private KeycloakTestContainer keycloak;

    @Test
    void userCanChangeOwnFirstNameInAccountConsole() throws Exception {
        String username = registerCandidate();

        keycloak.updateOwnAccount(tokenOf(username), account(username, emailOf(username), "Janusz"));

        assertThat(keycloak.userRepresentationOf(username).get("firstName")).isEqualTo("Janusz");
    }

    @Test
    void userCannotChangeOwnEmailInAccountConsole() throws Exception {
        String username = registerCandidate();

        keycloak.updateOwnAccount(tokenOf(username), account(username, "changed-" + emailOf(username), "Jan"));

        assertThat(keycloak.userRepresentationOf(username).get("email")).isEqualTo(emailOf(username));
    }

    @Test
    void locksUserOutAfterRepeatedFailedLogins() throws Exception {
        String username = registerCandidate();

        for (int attempt = 0; attempt < FAILED_LOGINS_BEFORE_LOCKOUT; attempt++) {
            assertThat(keycloak.passwordLoginStatus(username, "Wrong-pass-" + attempt).is4xxClientError()).isTrue();
        }

        assertThat(keycloak.passwordLoginStatus(username, PASSWORD).is4xxClientError()).isTrue();
    }

    private String registerCandidate() throws Exception {
        String username = "user" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        mockMvc.perform(post("/api/auth/register/candidate").contentType(MediaType.APPLICATION_JSON).content("""
                        {"username": "%s", "email": "%s", "password": "%s", "firstName": "Jan", "lastName": "Kowalski"}
                        """.formatted(username, emailOf(username), PASSWORD)))
                .andExpect(status().isCreated());
        return username;
    }

    private String tokenOf(String username) {
        return keycloak.loginThroughWebClient(username, PASSWORD);
    }

    private static Map<String, Object> account(String username, String email, String firstName) {
        return Map.of("username", username, "email", email, "firstName", firstName, "lastName", "Kowalski");
    }

    private static String emailOf(String username) {
        return username + "@example.com";
    }
}
