package com.candidate_manager.shared.event;

import com.candidate_manager.shared.config.messaging.event.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record CandidateRegisteredEvent(
        UUID eventId,
        Instant occurredAt,
        UUID userId,
        String email,
        String firstName,
        String lastName) implements DomainEvent {

    public static final String EXCHANGE = "cm.auth";
    public static final String ROUTING_KEY = "auth.candidate-registered";

    @Override
    public UUID getEventId() {
        return eventId;
    }

    @Override
    public Instant getOccurredAt() {
        return occurredAt;
    }

    @Override
    public String getExchange() {
        return EXCHANGE;
    }

    @Override
    public String getRoutingKey() {
        return ROUTING_KEY;
    }

    @Override
    public String toString() {
        return "CandidateRegisteredEvent[eventId=%s, occurredAt=%s, userId=%s]".formatted(eventId, occurredAt, userId);
    }
}
