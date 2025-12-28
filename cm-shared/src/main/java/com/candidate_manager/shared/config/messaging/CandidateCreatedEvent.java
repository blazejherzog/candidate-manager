package com.candidate_manager.shared.config.messaging;

import java.util.UUID;

public record CandidateCreatedEvent(
        UUID eventId,
        String candidateId,
        String firstName,
        String lastName,
        String email) implements DomainEvent {

    @Override
    public UUID getEventId() {
        return eventId;
    }
}
