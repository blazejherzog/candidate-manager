package com.candidate_manager.messaging;

import com.candidate_manager.TestcontainersConfiguration;
import com.candidate_manager.shared.config.messaging.retention.MessagingRetention;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MessagingRetentionIntegrationTest {

    @Autowired
    private MessagingRetention messagingRetention;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM outbox_event");
        jdbcTemplate.update("DELETE FROM processed_event");
    }

    @Test
    void deletesOnlyPublishedOutboxEventsOlderThanTtl() {
        UUID expired = insertOutboxEvent("now() - interval '8 days'", "now() - interval '8 days'");
        UUID recentlyPublished = insertOutboxEvent("now() - interval '8 days'", "now() - interval '6 days'");
        UUID oldButPending = insertOutboxEvent("now() - interval '30 days'", null);

        messagingRetention.deleteExpiredRows();

        assertThat(outboxEventExists(expired)).isFalse();
        assertThat(outboxEventExists(recentlyPublished)).isTrue();
        assertThat(outboxEventExists(oldButPending)).isTrue();
    }

    @Test
    void deletesOnlyProcessedEventsOlderThanTtl() {
        UUID expired = insertProcessedEvent("now() - interval '31 days'");
        UUID recent = insertProcessedEvent("now() - interval '29 days'");

        messagingRetention.deleteExpiredRows();

        assertThat(processedEventExists(expired)).isFalse();
        assertThat(processedEventExists(recent)).isTrue();
    }

    private UUID insertOutboxEvent(String createdAtSql, String publishedAtSql) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO outbox_event (id, event_type, exchange, routing_key, payload, created_at, published_at) "
                + "VALUES (?, 'TestEvent', 'test.exchange', 'test.key', '{}'::jsonb, " + createdAtSql + ", "
                + (publishedAtSql == null ? "NULL" : publishedAtSql) + ")", id);
        return id;
    }

    private UUID insertProcessedEvent(String processedAtSql) {
        UUID eventId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO processed_event (event_id, consumer, processed_at) VALUES (?, 'test-consumer', "
                + processedAtSql + ")", eventId);
        return eventId;
    }

    private boolean outboxEventExists(UUID id) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM outbox_event WHERE id = ?", Integer.class, id) == 1;
    }

    private boolean processedEventExists(UUID eventId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM processed_event WHERE event_id = ?", Integer.class, eventId) == 1;
    }
}
