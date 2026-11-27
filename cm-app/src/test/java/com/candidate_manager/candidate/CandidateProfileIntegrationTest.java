package com.candidate_manager.candidate;

import com.candidate_manager.KeycloakTestContainer;
import com.candidate_manager.TestcontainersConfiguration;
import com.candidate_manager.candidate.application.CandidateProfileService;
import com.candidate_manager.shared.config.messaging.outbox.OutboxRelay;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static com.candidate_manager.messaging.OutboxRelayTestSupport.relayAllPendingEvents;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CandidateProfileIntegrationTest {

    private static final String OWN_PROFILE = "/api/candidates/me";
    private static final String PASSWORD = "Secret-pass-1";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CandidateProfileService candidateProfileService;

    @Autowired
    private OutboxRelay outboxRelay;

    @Autowired
    private KeycloakTestContainer keycloak;

    @Test
    void registeredCandidateReadsOwnProfileWithKeycloakToken() throws Exception {
        String username = "user" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        mockMvc.perform(post("/api/auth/register/candidate").contentType(MediaType.APPLICATION_JSON).content("""
                        {"username": "%s", "email": "%s@example.com", "password": "%s", "firstName": "Jan", "lastName": "Kowalski"}
                        """.formatted(username, username, PASSWORD)))
                .andExpect(status().isCreated());
        relayAllPendingEvents(outboxRelay);
        UUID userId = keycloak.userIdOf(username);
        await().atMost(Duration.ofSeconds(10)).until(() -> profileExists(userId));

        mockMvc.perform(get(OWN_PROFILE)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + keycloak.loginThroughWebClient(username, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(username + "@example.com"))
                .andExpect(jsonPath("$.firstName").value("Jan"))
                .andExpect(jsonPath("$.lastName").value("Kowalski"))
                .andExpect(jsonPath("$.phoneNumber").isEmpty())
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void answersNotFoundWhenProfileDoesNotExistYet() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(get(OWN_PROFILE).with(candidate(userId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
        updateProfile(userId, validUpdate(0)).andExpect(status().isNotFound());
    }

    @Test
    void rejectsRequestsWithoutToken() throws Exception {
        mockMvc.perform(get(OWN_PROFILE)).andExpect(status().isUnauthorized());
        mockMvc.perform(put(OWN_PROFILE).contentType(MediaType.APPLICATION_JSON).content(json(validUpdate(0))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void forbidsRecruiterToUseCandidateProfile() throws Exception {
        UUID userId = createProfile();

        mockMvc.perform(get(OWN_PROFILE).with(recruiter(userId))).andExpect(status().isForbidden());
        mockMvc.perform(put(OWN_PROFILE).with(recruiter(userId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(validUpdate(0))))
                .andExpect(status().isForbidden());
        assertThat(profileRow(userId)).containsEntry("first_name", "Jan");
    }

    @Test
    void updatesAllEditableFieldsAndReturnsNextVersion() throws Exception {
        UUID userId = createProfile();

        updateProfile(userId, validUpdate(0))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Anna"))
                .andExpect(jsonPath("$.version").value(1));

        mockMvc.perform(get(OWN_PROFILE).with(candidate(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Anna"))
                .andExpect(jsonPath("$.lastName").value("Nowak"))
                .andExpect(jsonPath("$.phoneNumber").value("+48123456789"))
                .andExpect(jsonPath("$.city").value("Kraków"))
                .andExpect(jsonPath("$.headline").value("Java Developer"))
                .andExpect(jsonPath("$.summary").value("Five years of backend experience."))
                .andExpect(jsonPath("$.version").value(1));
        updateProfile(userId, validUpdate(1)).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2));
    }

    @Test
    void stripsTextAndClearsOptionalFieldsWhenBlankOrMissing() throws Exception {
        UUID userId = createProfile();
        updateProfile(userId, validUpdate(0)).andExpect(status().isOk());
        Map<String, Object> update = new HashMap<>();
        update.put("firstName", "  Ewa  ");
        update.put("lastName", "Nowak");
        update.put("phoneNumber", "");
        update.put("city", "   ");
        update.put("version", 1);

        updateProfile(userId, update).andExpect(status().isOk());

        assertThat(profileRow(userId))
                .containsEntry("first_name", "Ewa")
                .containsEntry("phone_number", null)
                .containsEntry("city", null)
                .containsEntry("headline", null)
                .containsEntry("summary", null);
    }

    @ParameterizedTest(name = "{0} = {1}")
    @MethodSource("invalidFieldValues")
    void rejectsInvalidFieldValue(String field, Object invalidValue) throws Exception {
        UUID userId = createProfile();
        Map<String, Object> update = validUpdate(0);
        update.put(field, invalidValue);

        updateProfile(userId, update)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem(field)));

        assertThat(profileRow(userId)).containsEntry("first_name", "Jan").containsEntry("version", 0L);
    }

    static Stream<Arguments> invalidFieldValues() {
        return Stream.of(
                Arguments.of("firstName", "   "),
                Arguments.of("firstName", "a".repeat(256)),
                Arguments.of("lastName", null),
                Arguments.of("phoneNumber", "123456789"),
                Arguments.of("phoneNumber", "+48 123 456 789"),
                Arguments.of("phoneNumber", "+0123456789"),
                Arguments.of("phoneNumber", "+1234567890123456"),
                Arguments.of("city", "a".repeat(101)),
                Arguments.of("headline", "a".repeat(121)),
                Arguments.of("summary", "a".repeat(2001)),
                Arguments.of("version", null));
    }

    @Test
    void ignoresEmailAndUserIdSentByCandidate() throws Exception {
        UUID userId = createProfile();
        Map<String, Object> update = validUpdate(0);
        update.put("email", "changed@example.com");
        update.put("userId", UUID.randomUUID().toString());

        updateProfile(userId, update).andExpect(status().isOk());

        assertThat(profileRow(userId)).containsEntry("email", emailOf(userId)).containsEntry("user_id", userId);
    }

    @Test
    void changesOnlyProfileOfAuthenticatedCandidate() throws Exception {
        UUID userId = createProfile();
        UUID otherUserId = createProfile();

        updateProfile(userId, validUpdate(0)).andExpect(status().isOk());

        assertThat(profileRow(otherUserId)).containsEntry("first_name", "Jan").containsEntry("version", 0L);
    }

    @Test
    void rejectsUpdateBasedOnOutdatedVersion() throws Exception {
        UUID userId = createProfile();
        updateProfile(userId, validUpdate(0)).andExpect(status().isOk());
        Map<String, Object> outdatedUpdate = validUpdate(0);
        outdatedUpdate.put("firstName", "Outdated");

        updateProfile(userId, outdatedUpdate)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        assertThat(profileRow(userId)).containsEntry("first_name", "Anna").containsEntry("version", 1L);
    }

    private ResultActions updateProfile(UUID userId, Map<String, Object> update) throws Exception {
        return mockMvc.perform(put(OWN_PROFILE).with(candidate(userId))
                .contentType(MediaType.APPLICATION_JSON).content(json(update)));
    }

    private UUID createProfile() {
        UUID userId = UUID.randomUUID();
        candidateProfileService.createProfile(userId, emailOf(userId), "Jan", "Kowalski");
        return userId;
    }

    private boolean profileExists(UUID userId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM candidate WHERE user_id = ?", Integer.class, userId) == 1;
    }

    private Map<String, Object> profileRow(UUID userId) {
        return jdbcTemplate.queryForMap("SELECT * FROM candidate WHERE user_id = ?", userId);
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private static String emailOf(UUID userId) {
        return userId + "@example.com";
    }

    private static Map<String, Object> validUpdate(long version) {
        Map<String, Object> update = new HashMap<>();
        update.put("firstName", "Anna");
        update.put("lastName", "Nowak");
        update.put("phoneNumber", "+48123456789");
        update.put("city", "Kraków");
        update.put("headline", "Java Developer");
        update.put("summary", "Five years of backend experience.");
        update.put("version", version);
        return update;
    }

    private static RequestPostProcessor candidate(UUID userId) {
        return jwt().jwt(token -> token.subject(userId.toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_CANDIDATE"));
    }

    private static RequestPostProcessor recruiter(UUID userId) {
        return jwt().jwt(token -> token.subject(userId.toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_RECRUITER"));
    }
}
