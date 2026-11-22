package com.candidate_manager.messaging;

import com.candidate_manager.TestcontainersConfiguration;
import com.candidate_manager.shared.config.messaging.event.DomainEvent;
import com.candidate_manager.shared.config.messaging.outbox.OutboxRelay;
import com.candidate_manager.shared.config.messaging.publisher.EventPublisher;
import com.candidate_manager.shared.event.CandidateRegisteredEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class OutboxIntegrationTest {

    private static final String EXCHANGE_BUILT_INTO_EVERY_BROKER = "amq.topic";
    private static final String MISSING_EXCHANGE = "test.missing.exchange";
    private static final String ROUTING_KEY = "test.outbox";

    @Autowired
    private EventPublisher eventPublisher;

    @Autowired
    private OutboxRelay outboxRelay;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM outbox_event");
        jdbcTemplate.update("DELETE FROM processed_event");
        jdbcTemplate.update("DELETE FROM candidate");
    }

    @Test
    void storesEventInOutboxWhenTransactionCommits() {
        TestOutboxEvent event = eventTo(EXCHANGE_BUILT_INTO_EVERY_BROKER);

        publishInTransaction(event);

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT event_type, exchange, routing_key, payload::text AS payload, published_at "
                        + "FROM outbox_event WHERE id = ?",
                event.eventId());
        assertThat(row.get("event_type")).isEqualTo(TestOutboxEvent.class.getName());
        assertThat(row.get("exchange")).isEqualTo(EXCHANGE_BUILT_INTO_EVERY_BROKER);
        assertThat(row.get("routing_key")).isEqualTo(ROUTING_KEY);
        assertThat((String) row.get("payload")).contains(event.eventId().toString());
        assertThat(row.get("published_at")).isNull();
    }

    @Test
    void leavesNoOutboxEventWhenTransactionRollsBack() {
        TestOutboxEvent event = eventTo(EXCHANGE_BUILT_INTO_EVERY_BROKER);

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publish(event);
            throw new IllegalStateException("Business operation failed");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(countOutboxEvents()).isZero();
    }

    @Test
    void rejectsPublishingOutsideTransaction() {
        assertThatThrownBy(() -> eventPublisher.publish(eventTo(EXCHANGE_BUILT_INTO_EVERY_BROKER)))
                .isInstanceOf(IllegalTransactionStateException.class);

        assertThat(countOutboxEvents()).isZero();
    }

    @Test
    void rejectsDuplicateEventIdWithoutOverwritingStoredEvent() {
        UUID eventId = UUID.randomUUID();
        publishInTransaction(new TestOutboxEvent(eventId, Instant.now(), EXCHANGE_BUILT_INTO_EVERY_BROKER, ROUTING_KEY));

        assertThatThrownBy(() -> publishInTransaction(new TestOutboxEvent(eventId, Instant.now(), MISSING_EXCHANGE, ROUTING_KEY)))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(countOutboxEvents()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT exchange FROM outbox_event WHERE id = ?", String.class, eventId))
                .isEqualTo(EXCHANGE_BUILT_INTO_EVERY_BROKER);
    }

    @Test
    void storesPayloadWithOccurredAtAndWithoutRoutingDetails() {
        Instant occurredAt = Instant.parse("2026-01-02T03:04:05Z");
        CandidateRegisteredEvent event = candidateRegisteredEvent(occurredAt);

        publishInTransaction(event);

        Map<String, Object> payload = jdbcTemplate.queryForMap(
                "SELECT payload->>'occurredAt' AS occurred_at, jsonb_exists(payload, 'exchange') AS has_exchange, "
                        + "jsonb_exists(payload, 'routingKey') AS has_routing_key FROM outbox_event WHERE id = ?",
                event.eventId());
        assertThat(payload.get("occurred_at")).isEqualTo("2026-01-02T03:04:05Z");
        assertThat(payload.get("has_exchange")).isEqualTo(false);
        assertThat(payload.get("has_routing_key")).isEqualTo(false);
    }

    @Test
    void relayPublishesPendingEventAndMarksItPublished() {
        TestOutboxEvent event = eventTo(EXCHANGE_BUILT_INTO_EVERY_BROKER);
        publishInTransaction(event);

        int published = outboxRelay.publishPendingEvents();

        assertThat(published).isEqualTo(1);
        assertThat(publishedAt(event)).isNotNull();
    }

    @Test
    void deliversEventToIdempotentConsumerEndToEnd() {
        CandidateRegisteredEvent event = candidateRegisteredEvent(Instant.now());
        publishInTransaction(event);

        assertThat(outboxRelay.publishPendingEvents()).isEqualTo(1);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM processed_event WHERE event_id = ?", Integer.class, event.eventId()))
                        .isEqualTo(1));
    }

    @Test
    void keepsEventPendingWithoutThrowingWhenBrokerRejectsIt() {
        TestOutboxEvent event = eventTo(MISSING_EXCHANGE);
        publishInTransaction(event);

        int published = outboxRelay.publishPendingEvents();

        assertThat(published).isZero();
        assertThat(publishedAt(event)).isNull();
    }

    @Test
    void keepsEventsSentBeforeFailureMarkedAndDoesNotResendThem() {
        TestOutboxEvent sentEvent = eventTo(EXCHANGE_BUILT_INTO_EVERY_BROKER);
        TestOutboxEvent failingEventStoredLater = eventTo(MISSING_EXCHANGE);
        publishInTransaction(sentEvent);
        publishInTransaction(failingEventStoredLater);

        assertThat(outboxRelay.publishPendingEvents()).isEqualTo(1);
        Timestamp firstPublishedAt = publishedAt(sentEvent);
        assertThat(firstPublishedAt).isNotNull();
        assertThat(publishedAt(failingEventStoredLater)).isNull();

        assertThat(outboxRelay.publishPendingEvents()).isZero();
        assertThat(publishedAt(sentEvent)).isEqualTo(firstPublishedAt);
        assertThat(publishedAt(failingEventStoredLater)).isNull();
    }

    @Test
    void sendsEventsStoredInOneTransactionAndSharingCreatedAtInInsertionOrder() {
        TestOutboxEvent failingEvent = eventTo(MISSING_EXCHANGE);
        List<TestOutboxEvent> laterEvents = List.of(
                eventTo(EXCHANGE_BUILT_INTO_EVERY_BROKER), eventTo(EXCHANGE_BUILT_INTO_EVERY_BROKER), eventTo(EXCHANGE_BUILT_INTO_EVERY_BROKER));
        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publish(failingEvent);
            laterEvents.forEach(eventPublisher::publish);
        });

        assertThat(outboxRelay.publishPendingEvents()).isZero();
        assertThat(publishedAt(failingEvent)).isNull();
        laterEvents.forEach(event -> assertThat(publishedAt(event)).isNull());
    }

    @Test
    void relaySkipsEventLockedByAnotherTransaction() throws Exception {
        TestOutboxEvent lockedEvent = eventTo(EXCHANGE_BUILT_INTO_EVERY_BROKER);
        TestOutboxEvent freeEvent = eventTo(EXCHANGE_BUILT_INTO_EVERY_BROKER);
        publishInTransaction(lockedEvent);
        publishInTransaction(freeEvent);

        CountDownLatch rowLocked = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> anotherRelayHoldingRowLock = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                jdbcTemplate.queryForObject(
                        "SELECT id FROM outbox_event WHERE id = ? FOR UPDATE", UUID.class, lockedEvent.eventId());
                rowLocked.countDown();
                try {
                    releaseLock.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            }));
            assertThat(rowLocked.await(10, TimeUnit.SECONDS)).isTrue();

            int publishedWhileLocked = outboxRelay.publishPendingEvents();

            releaseLock.countDown();
            anotherRelayHoldingRowLock.get(10, TimeUnit.SECONDS);

            assertThat(publishedWhileLocked).isEqualTo(1);
        } finally {
            releaseLock.countDown();
            executor.shutdownNow();
        }

        assertThat(publishedAt(lockedEvent)).isNull();
        assertThat(publishedAt(freeEvent)).isNotNull();

        assertThat(outboxRelay.publishPendingEvents()).isEqualTo(1);
        assertThat(publishedAt(lockedEvent)).isNotNull();
    }

    @Test
    void concurrentRelaysPublishEachEventExactlyOnce() throws Exception {
        int eventCount = 20;
        for (int i = 0; i < eventCount; i++) {
            publishInTransaction(eventTo(EXCHANGE_BUILT_INTO_EVERY_BROKER));
        }

        CountDownLatch startSignal = new CountDownLatch(1);
        Callable<Integer> relayOnSignal = () -> {
            startSignal.await();
            return outboxRelay.publishPendingEvents();
        };

        ExecutorService executor = Executors.newFixedThreadPool(2);
        int totalPublished = 0;
        try {
            List<Future<Integer>> results = List.of(
                    executor.submit(relayOnSignal),
                    executor.submit(relayOnSignal));

            startSignal.countDown();

            for (Future<Integer> result : results) {
                totalPublished += result.get(30, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(totalPublished).isEqualTo(eventCount);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_event WHERE published_at IS NULL", Integer.class))
                .isZero();
    }

    private static CandidateRegisteredEvent candidateRegisteredEvent(Instant occurredAt) {
        UUID userId = UUID.randomUUID();
        return new CandidateRegisteredEvent(
                UUID.randomUUID(), occurredAt, userId, userId + "@example.com", "Jan", "Kowalski");
    }

    private TestOutboxEvent eventTo(String exchange) {
        return new TestOutboxEvent(UUID.randomUUID(), Instant.now(), exchange, ROUTING_KEY);
    }

    private void publishInTransaction(DomainEvent event) {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(event));
    }

    private int countOutboxEvents() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM outbox_event", Integer.class);
    }

    private Timestamp publishedAt(TestOutboxEvent event) {
        return jdbcTemplate.queryForObject(
                "SELECT published_at FROM outbox_event WHERE id = ?", Timestamp.class, event.eventId());
    }
}
