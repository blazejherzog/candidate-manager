package com.candidate_manager.shared.config.messaging.processed;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ProcessedEventRepositoryImpl implements ProcessedEventRepository {

    private final JdbcClient jdbcClient;

    @Override
    public boolean tryMarkAsProcessed(UUID eventId, String consumer) {
        int inserted = jdbcClient.sql("""
                        INSERT INTO processed_event (event_id, consumer, processed_at)
                        VALUES (:eventId, :consumer, now())
                        ON CONFLICT (event_id, consumer) DO NOTHING
                        """)
                .param("eventId", eventId)
                .param("consumer", consumer)
                .update();
        return inserted == 1;
    }
}
