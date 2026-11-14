package com.candidate_manager.shared.config.messaging.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventEntity, UUID> {

    @Query(value = """
            SELECT *
            FROM outbox_event
            WHERE published_at IS NULL
            ORDER BY sequence_number
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEventEntity> findPendingForUpdate(@Param("limit") int limit);

    @Modifying
    @Query(value = """
            UPDATE outbox_event
            SET published_at = now()
            WHERE id = :id
              AND published_at IS NULL
            """, nativeQuery = true)
    int markPublished(@Param("id") UUID id);

    @Modifying
    @Query(value = """
            INSERT INTO outbox_event (id, event_type, exchange, routing_key, payload, created_at)
            VALUES (:id, :eventType, :exchange, :routingKey, CAST(:payload AS jsonb), now())
            """, nativeQuery = true)
    void insert(@Param("id") UUID id,
                @Param("eventType") String eventType,
                @Param("exchange") String exchange,
                @Param("routingKey") String routingKey,
                @Param("payload") String payload);
}
