package com.candidate_manager.shared.config.messaging.processed;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ProcessedEventJpaRepository extends JpaRepository<ProcessedEventEntity, UUID> {

    @Modifying
    @Query(value = "INSERT INTO processed_event (event_id, processed_at) VALUES (:eventId, now()) ON CONFLICT (event_id) DO NOTHING", nativeQuery = true)
    int insertProcessedEvent(@Param("eventId") UUID eventId);
}
