package com.candidate_manager.messaging;

import com.candidate_manager.TestcontainersConfiguration;
import com.candidate_manager.shared.config.messaging.event.CandidateCreatedEvent;
import com.candidate_manager.shared.config.messaging.event.DomainEvent;
import com.candidate_manager.shared.config.messaging.outbox.OutboxRelay;
import com.candidate_manager.shared.config.messaging.publisher.EventPublisher;
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

    // Built into every RabbitMQ broker, so the broker accepts and confirms messages sent to it.
    private static final String EXISTING_EXCHANGE = "amq.topic";
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
    }

    @Test
    void storesEventInOutboxWhenTransactionCommits() {
        TestOutboxEvent event = eventTo(EXISTING_EXCHANGE);

        publishInTransaction(event);

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT event_type, exchange, routing_key, payload::text AS payload, published_at "
                        + "FROM outbox_event WHERE id = ?",
                event.eventId());
        assertThat(row.get("event_type")).isEqualTo(TestOutboxEvent.class.getName());
        assertThat(row.get("exchange")).isEqualTo(EXISTING_EXCHANGE);
        assertThat(row.get("routing_key")).isEqualTo(ROUTING_KEY);
        assertThat((String) row.get("payload")).contains(event.eventId().toString());
        assertThat(row.get("published_at")).isNull();
    }

    @Test
    void leavesNoOutboxEventWhenTransactionRollsBack() {
        TestOutboxEvent event = eventTo(EXISTING_EXCHANGE);

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publish(event);
            throw new IllegalStateException("Business operation failed");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(countOutboxEvents()).isZero();
    }

    @Test
    void rejectsPublishingOutsideTransaction() {
        assertThatThrownBy(() -> eventPublisher.publish(eventTo(EXISTING_EXCHANGE)))
                .isInstanceOf(IllegalTransactionStateException.class);

        assertThat(countOutboxEvents()).isZero();
    }

    @Test
    void rejectsDuplicateEventIdWithoutOverwritingStoredEvent() {
        UUID eventId = UUID.randomUUID();
        publishInTransaction(new TestOutboxEvent(eventId, EXISTING_EXCHANGE, ROUTING_KEY));

        assertThatThrownBy(() -> publishInTransaction(new TestOutboxEvent(eventId, MISSING_EXCHANGE, ROUTING_KEY)))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(countOutboxEvents()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT exchange FROM outbox_event WHERE id = ?", String.class, eventId))
                .isEqualTo(EXISTING_EXCHANGE);
    }

    @Test
    void relayPublishesPendingEventAndMarksItPublished() {
        TestOutboxEvent event = eventTo(EXISTING_EXCHANGE);
        publishInTransaction(event);

        int published = outboxRelay.publishPendingEvents();

        assertThat(published).isEqualTo(1);
        assertThat(publishedAt(event)).isNotNull();
    }

    @Test
    void deliversEventToIdempotentConsumerEndToEnd() {
        CandidateCreatedEvent event = new CandidateCreatedEvent(
                UUID.randomUUID(), "candidate-1", "Jan", "Kowalski", "jan.kowalski@example.com");
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
        TestOutboxEvent sentEvent = eventTo(EXISTING_EXCHANGE);
        TestOutboxEvent failingEvent = eventTo(MISSING_EXCHANGE);
        // Stored after sentEvent, so it gets a higher sequence_number and is sent second.
        publishInTransaction(sentEvent);
        publishInTransaction(failingEvent);

        assertThat(outboxRelay.publishPendingEvents()).isEqualTo(1);
        Timestamp firstPublishedAt = publishedAt(sentEvent);
        assertThat(firstPublishedAt).isNotNull();
        assertThat(publishedAt(failingEvent)).isNull();

        assertThat(outboxRelay.publishPendingEvents()).isZero();
        assertThat(publishedAt(sentEvent)).isEqualTo(firstPublishedAt);
        assertThat(publishedAt(failingEvent)).isNull();
    }

    @Test
    void sendsEventsStoredInOneTransactionInInsertionOrder() {
        // Events stored in one transaction share created_at (now() is the transaction start time),
        // so only sequence_number decides that the failing event is sent first and stops the batch.
        TestOutboxEvent failingEvent = eventTo(MISSING_EXCHANGE);
        List<TestOutboxEvent> laterEvents = List.of(
                eventTo(EXISTING_EXCHANGE), eventTo(EXISTING_EXCHANGE), eventTo(EXISTING_EXCHANGE));
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
        TestOutboxEvent lockedEvent = eventTo(EXISTING_EXCHANGE);
        TestOutboxEvent freeEvent = eventTo(EXISTING_EXCHANGE);
        publishInTransaction(lockedEvent);
        publishInTransaction(freeEvent);

        CountDownLatch rowLocked = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            // Holds a row lock on lockedEvent in its own transaction, as another relay instance would.
            Future<?> lockHolder = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
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
            lockHolder.get(10, TimeUnit.SECONDS);

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
            publishInTransaction(eventTo(EXISTING_EXCHANGE));
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

        // Without FOR UPDATE SKIP LOCKED both relays would send the same rows and the sum would exceed eventCount.
        assertThat(totalPublished).isEqualTo(eventCount);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_event WHERE published_at IS NULL", Integer.class))
                .isZero();
    }

    private TestOutboxEvent eventTo(String exchange) {
        return new TestOutboxEvent(UUID.randomUUID(), exchange, ROUTING_KEY);
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
