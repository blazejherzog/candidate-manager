package com.candidate_manager.shared.config.messaging.processed;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "processed_event")
@NoArgsConstructor(access = AccessLevel.PROTECTED) // dla JPA
@AllArgsConstructor
public class ProcessedEventEntity {

    @Id
    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    private LocalDateTime processedAt = LocalDateTime.now();

    public ProcessedEventEntity(UUID eventId) {
        this.eventId = eventId;
        this.processedAt = LocalDateTime.now();
    }
}
