package com.candidate_manager.shared.config.messaging.retention;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

@Slf4j
@Component
public class MessagingRetention {

    private final JdbcClient jdbcClient;
    private final Duration publishedOutboxEventTtl;
    private final Duration processedEventTtl;
    private final int batchSize;

    public MessagingRetention(JdbcClient jdbcClient,
                              @Value("${messaging.retention.published-outbox-event-ttl}") Duration publishedOutboxEventTtl,
                              @Value("${messaging.retention.processed-event-ttl}") Duration processedEventTtl,
                              @Value("${messaging.retention.batch-size}") int batchSize) {
        this.jdbcClient = jdbcClient;
        this.publishedOutboxEventTtl = publishedOutboxEventTtl;
        this.processedEventTtl = processedEventTtl;
        this.batchSize = batchSize;
    }

    public void deleteExpiredRows() {
        Instant now = Instant.now();
        int outboxEvents = deleteInBatches("""
                DELETE FROM outbox_event
                WHERE id IN (SELECT id FROM outbox_event WHERE published_at < :cutoff LIMIT :batchSize)
                """, now.minus(publishedOutboxEventTtl));
        int processedEvents = deleteInBatches("""
                DELETE FROM processed_event
                WHERE (event_id, consumer) IN
                      (SELECT event_id, consumer FROM processed_event WHERE processed_at < :cutoff LIMIT :batchSize)
                """, now.minus(processedEventTtl));
        log.info("Messaging retention deleted {} published outbox events and {} processed events", outboxEvents, processedEvents);
    }

    private int deleteInBatches(String deleteBatchSql, Instant cutoff) {
        int total = 0;
        int deleted;
        do {
            deleted = jdbcClient.sql(deleteBatchSql)
                    .param("cutoff", Timestamp.from(cutoff))
                    .param("batchSize", batchSize)
                    .update();
            total += deleted;
        } while (deleted == batchSize);
        return total;
    }
}
