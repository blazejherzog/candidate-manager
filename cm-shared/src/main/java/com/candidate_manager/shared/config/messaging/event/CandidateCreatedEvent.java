package com.candidate_manager.shared.config.messaging.event;

import java.time.Instant;
import java.util.UUID;

public record CandidateCreatedEvent(
        UUID eventId,
        Instant occurredAt,
        String candidateId,
        String firstName,
        String lastName,
        String email) implements DomainEvent {

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
        return "candidate.exchange";
    }

    @Override
    public String getRoutingKey() {
        return "candidate.created";
    }
}
