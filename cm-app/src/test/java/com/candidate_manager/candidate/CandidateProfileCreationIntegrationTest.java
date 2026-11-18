package com.candidate_manager.candidate;

import com.candidate_manager.KeycloakTestContainer;
import com.candidate_manager.TestcontainersConfiguration;
import com.candidate_manager.shared.config.messaging.outbox.OutboxRelay;
import com.candidate_manager.shared.event.CandidateRegisteredEvent;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CandidateProfileCreationIntegrationTest {

    private static final String CANDIDATE_REGISTERED_DEAD_LETTER_QUEUE = "cm-candidate.candidate-registered.dlq";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private KeycloakTestContainer keycloak;

    @Autowired
    private OutboxRelay outboxRelay;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsCandidateProfileForRegisteredCandidate() throws Exception {
        String username = uniqueUsername();

        register("/api/auth/register/candidate", username);
        outboxRelay.publishPendingEvents();

        await().atMost(Duration.ofSeconds(10)).until(() -> countProfilesWithEmail(emailOf(username)) == 1);
        Map<String, Object> profile = jdbcTemplate.queryForMap(
                "SELECT user_id, first_name, last_name FROM candidate WHERE email = ?", emailOf(username));
        assertThat(profile.get("user_id")).isEqualTo(keycloak.userIdOf(username));
        assertThat(profile.get("first_name")).isEqualTo("Jan");
        assertThat(profile.get("last_name")).isEqualTo("Kowalski");
    }

    @Test
    void ignoresRedeliveredEventWithoutCreatingSecondProfileOrDeadLetter() throws Exception {
        String username = uniqueUsername();
        register("/api/auth/register/candidate", username);
        String payload = candidateRegisteredPayloadFor(emailOf(username));
        outboxRelay.publishPendingEvents();
        await().atMost(Duration.ofSeconds(10)).until(() -> countProfilesWithEmail(emailOf(username)) == 1);
        rabbitAdmin.purgeQueue(CANDIDATE_REGISTERED_DEAD_LETTER_QUEUE, false);

        rabbitTemplate.send(CandidateRegisteredEvent.EXCHANGE, CandidateRegisteredEvent.ROUTING_KEY, jsonMessage(payload));

        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5))
                .until(() -> countProfilesWithEmail(emailOf(username)) == 1);
        assertThat(rabbitTemplate.receive(CANDIDATE_REGISTERED_DEAD_LETTER_QUEUE, 5_000)).isNull();
    }

    @Test
    void doesNotPublishCandidateRegisteredEventForRecruiter() throws Exception {
        String username = uniqueUsername();

        register("/api/auth/register/recruiter", username);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_event WHERE payload->>'email' = ?", Integer.class, emailOf(username)))
                .isZero();
    }

    private void register(String endpoint, String username) throws Exception {
        mockMvc.perform(post(endpoint).contentType(MediaType.APPLICATION_JSON).content("""
                        {"username": "%s", "email": "%s", "password": "Secret-pass-1", "firstName": "Jan", "lastName": "Kowalski"}
                        """.formatted(username, emailOf(username))))
                .andExpect(status().isCreated());
    }

    private String candidateRegisteredPayloadFor(String email) {
        return jdbcTemplate.queryForObject(
                "SELECT payload::text FROM outbox_event WHERE event_type = ? AND payload->>'email' = ?",
                String.class, CandidateRegisteredEvent.class.getName(), email);
    }

    private int countProfilesWithEmail(String email) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM candidate WHERE email = ?", Integer.class, email);
    }

    private static Message jsonMessage(String body) {
        return MessageBuilder.withBody(body.getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setMessageId(UUID.randomUUID().toString())
                .build();
    }

    private static String emailOf(String username) {
        return username + "@example.com";
    }

    private static String uniqueUsername() {
        return "user" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
