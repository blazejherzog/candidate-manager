package com.candidate_manager.event;

import com.candidate_manager.TestcontainersConfiguration;
import com.candidate_manager.jobapplication.JobApplicationTestData;
import com.candidate_manager.jobapplication.application.AlreadyAppliedException;
import com.candidate_manager.jobapplication.application.JobApplicationService;
import com.candidate_manager.jobapplication.domain.JobApplicationStatus;
import com.candidate_manager.jobapplication.domain.JobApplicationStatusChangeNotAllowedException;
import com.candidate_manager.shared.config.messaging.outbox.OutboxRelay;
import com.candidate_manager.shared.event.JobApplicationStatusChangedEvent;
import com.candidate_manager.shared.event.JobApplicationSubmittedEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import static com.candidate_manager.messaging.OutboxRelayTestSupport.relayAllPendingEvents;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ApplicationHistoryIntegrationTest {

    private static final String SUBMITTED_DEAD_LETTER_QUEUE = "cm-event.application-submitted.dlq";
    private static final String STATUS_CHANGED_DEAD_LETTER_QUEUE = "cm-event.application-status-changed.dlq";
    private static final Duration DELIVERY_TIMEOUT = Duration.ofSeconds(10);
    private static final long DEAD_LETTER_TIMEOUT_MILLIS = 10_000;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @Autowired
    private OutboxRelay outboxRelay;

    @Autowired
    private JobApplicationService jobApplicationService;

    @Autowired
    private JobApplicationTestData testData;

    private UUID recruiterId;
    private UUID candidateUserId;
    private UUID offerId;
    private UUID applicationId;

    @BeforeEach
    void submitApplication() {
        recruiterId = UUID.randomUUID();
        candidateUserId = testData.registeredCandidate();
        offerId = testData.publishedOfferOf(recruiterId);
        applicationId = jobApplicationService.apply(offerId, candidateUserId, "Call me: +48123456789").id();
        rabbitAdmin.purgeQueue(SUBMITTED_DEAD_LETTER_QUEUE, false);
        rabbitAdmin.purgeQueue(STATUS_CHANGED_DEAD_LETTER_QUEUE, false);
    }

    @Test
    void showsWholeHistoryToCandidateAndRecruiter() throws Exception {
        changeStatus(JobApplicationStatus.IN_REVIEW, 0);
        changeStatus(JobApplicationStatus.INTERVIEW, 1);
        jobApplicationService.withdraw(applicationId, candidateUserId);

        relayAllPendingEvents(outboxRelay);
        awaitHistorySize(4);

        for (RequestPostProcessor viewer : new RequestPostProcessor[]{candidate(candidateUserId), recruiter(recruiterId)}) {
            mockMvc.perform(get(history()).with(viewer))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(4))
                    .andExpect(jsonPath("$[0].previousStatus").isEmpty())
                    .andExpect(jsonPath("$[0].status").value("SUBMITTED"))
                    .andExpect(jsonPath("$[0].changedBy").value("CANDIDATE"))
                    .andExpect(jsonPath("$[0].occurredAt").exists())
                    .andExpect(jsonPath("$[1].previousStatus").value("SUBMITTED"))
                    .andExpect(jsonPath("$[1].status").value("IN_REVIEW"))
                    .andExpect(jsonPath("$[1].changedBy").value("RECRUITER"))
                    .andExpect(jsonPath("$[2].status").value("INTERVIEW"))
                    .andExpect(jsonPath("$[3].previousStatus").value("INTERVIEW"))
                    .andExpect(jsonPath("$[3].status").value("WITHDRAWN"))
                    .andExpect(jsonPath("$[3].changedBy").value("CANDIDATE"))
                    .andExpect(jsonPath("$[3].changedByUserId").doesNotExist());
        }
        assertThat(jdbcTemplate.queryForList(
                "SELECT changed_by_user_id FROM application_history_entry WHERE job_application_id = ? ORDER BY application_version",
                UUID.class, applicationId)).containsExactly(candidateUserId, recruiterId, recruiterId, candidateUserId);
    }

    @Test
    void returnsEmptyHistoryUntilEventsAreConsumed() throws Exception {
        mockMvc.perform(get(history()).with(candidate(candidateUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void ordersHistoryByApplicationVersionWhenEventsArriveInReverseOrder() throws Exception {
        changeStatus(JobApplicationStatus.IN_REVIEW, 0);

        deliverStatusChanged(payloadOf(JobApplicationStatusChangedEvent.class));
        awaitHistorySize(1);
        deliverSubmitted(payloadOf(JobApplicationSubmittedEvent.class));
        awaitHistorySize(2);

        mockMvc.perform(get(history()).with(recruiter(recruiterId)))
                .andExpect(jsonPath("$[0].status").value("SUBMITTED"))
                .andExpect(jsonPath("$[1].status").value("IN_REVIEW"));
    }

    @Test
    void ignoresRedeliveredEventWithoutDeadLetteringIt() throws Exception {
        JsonNode payload = payloadOf(JobApplicationSubmittedEvent.class);
        deliverSubmitted(payload);
        awaitHistorySize(1);

        deliverSubmitted(payload);

        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5)).until(() -> historySize() == 1);
        assertThat(rabbitTemplate.receive(SUBMITTED_DEAD_LETTER_QUEUE, DEAD_LETTER_TIMEOUT_MILLIS / 2)).isNull();
    }

    @Test
    void deadLettersDifferentEventDescribingAlreadyRecordedVersion() throws Exception {
        JsonNode payload = payloadOf(JobApplicationSubmittedEvent.class);
        deliverSubmitted(payload);
        awaitHistorySize(1);
        ObjectNode otherEventWithSameVersion = payload.deepCopy();
        otherEventWithSameVersion.put("eventId", UUID.randomUUID().toString());

        deliverSubmitted(otherEventWithSameVersion);

        assertThat(rabbitTemplate.receive(SUBMITTED_DEAD_LETTER_QUEUE, DEAD_LETTER_TIMEOUT_MILLIS)).isNotNull();
        assertThat(historySize()).isEqualTo(1);
    }

    @Test
    void recordsStatusUnknownToHistoryModule() throws Exception {
        changeStatus(JobApplicationStatus.IN_REVIEW, 0);
        ObjectNode payload = payloadOf(JobApplicationStatusChangedEvent.class).deepCopy();
        payload.put("status", "OFFER_SENT");

        deliverStatusChanged(payload);
        awaitHistorySize(1);

        mockMvc.perform(get(history()).with(recruiter(recruiterId)))
                .andExpect(jsonPath("$[0].status").value("OFFER_SENT"));
    }

    @Test
    void rejectedRequestsStoreNoEvent() {
        changeStatus(JobApplicationStatus.INTERVIEW, 0);
        int eventsBeforeRejectedRequests = countOutboxEvents();

        assertThatThrownBy(() -> changeStatus(JobApplicationStatus.HIRED, 0))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        assertThatThrownBy(() -> changeStatus(JobApplicationStatus.IN_REVIEW, 1))
                .isInstanceOf(JobApplicationStatusChangeNotAllowedException.class);
        assertThatThrownBy(() -> jobApplicationService.apply(offerId, candidateUserId, null))
                .isInstanceOf(AlreadyAppliedException.class);

        assertThat(countOutboxEvents()).isEqualTo(eventsBeforeRejectedRequests);
    }

    @Test
    void eventsContainOnlyIdentifiersAndStatuses() throws Exception {
        changeStatus(JobApplicationStatus.IN_REVIEW, 0);

        assertThat(payloadOf(JobApplicationSubmittedEvent.class).fieldNames()).toIterable().containsExactlyInAnyOrder(
                "eventId", "occurredAt", "jobApplicationId", "applicationVersion", "jobOfferId", "candidateId",
                "recruiterId", "status", "submittedByUserId");
        assertThat(payloadOf(JobApplicationStatusChangedEvent.class).fieldNames()).toIterable().containsExactlyInAnyOrder(
                "eventId", "occurredAt", "jobApplicationId", "applicationVersion", "jobOfferId", "candidateId",
                "recruiterId", "previousStatus", "status", "changedBy", "changedByUserId");
    }

    @Test
    void hidesHistoryFromOtherUsers() throws Exception {
        mockMvc.perform(get(history()).with(candidate(testData.registeredCandidate()))).andExpect(status().isNotFound());
        mockMvc.perform(get(history()).with(candidate(recruiterId))).andExpect(status().isNotFound());
        mockMvc.perform(get(history()).with(recruiter(UUID.randomUUID()))).andExpect(status().isNotFound());
        mockMvc.perform(get(history()).with(recruiter(candidateUserId))).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/applications/" + UUID.randomUUID() + "/history").with(recruiter(recruiterId)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(history())).andExpect(status().isUnauthorized());
    }

    private void changeStatus(JobApplicationStatus newStatus, long version) {
        jobApplicationService.changeStatus(applicationId, recruiterId, newStatus, version);
    }

    private JsonNode payloadOf(Class<?> eventType) throws Exception {
        return objectMapper.readTree(jdbcTemplate.queryForObject(
                "SELECT payload::text FROM outbox_event WHERE event_type = ? AND payload->>'jobApplicationId' = ?",
                String.class, eventType.getName(), applicationId.toString()));
    }

    private int countOutboxEvents() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM outbox_event WHERE payload->>'jobOfferId' = ?",
                Integer.class, offerId.toString());
    }

    private void deliverSubmitted(JsonNode payload) {
        deliver(JobApplicationSubmittedEvent.EXCHANGE, JobApplicationSubmittedEvent.ROUTING_KEY, payload);
    }

    private void deliverStatusChanged(JsonNode payload) {
        deliver(JobApplicationStatusChangedEvent.EXCHANGE, JobApplicationStatusChangedEvent.ROUTING_KEY, payload);
    }

    private void deliver(String exchange, String routingKey, JsonNode payload) {
        rabbitTemplate.send(exchange, routingKey, MessageBuilder
                .withBody(payload.toString().getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .build());
    }

    private void awaitHistorySize(int expectedSize) {
        await().atMost(DELIVERY_TIMEOUT).until(() -> historySize() == expectedSize);
    }

    private int historySize() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM application_history_entry WHERE job_application_id = ?", Integer.class, applicationId);
    }

    private String history() {
        return "/api/applications/" + applicationId + "/history";
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
