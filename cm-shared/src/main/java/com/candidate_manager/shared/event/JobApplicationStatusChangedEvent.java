package com.candidate_manager.shared.event;

import com.candidate_manager.shared.config.messaging.event.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record JobApplicationStatusChangedEvent(
        UUID eventId,
        Instant occurredAt,
        UUID jobApplicationId,
        long applicationVersion,
        UUID jobOfferId,
        UUID candidateId,
        UUID recruiterId,
        String previousStatus,
        String status,
        JobApplicationActor changedBy,
        UUID changedByUserId) implements DomainEvent {

    public static final String EXCHANGE = "cm.application";
    public static final String ROUTING_KEY = "application.application-status-changed";

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
}
