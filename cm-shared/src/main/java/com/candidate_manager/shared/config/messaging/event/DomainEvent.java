package com.candidate_manager.shared.config.messaging.event;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.Instant;
import java.util.UUID;

public interface DomainEvent {
    UUID getEventId();

    Instant getOccurredAt();

    @JsonIgnore
    String getExchange();

    @JsonIgnore
    String getRoutingKey();
}
