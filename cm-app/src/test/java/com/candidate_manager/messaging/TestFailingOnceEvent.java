package com.candidate_manager.messaging;

import com.candidate_manager.shared.config.messaging.event.DomainEvent;

import java.util.UUID;

public record TestFailingOnceEvent(UUID eventId) implements DomainEvent {

    @Override
    public UUID getEventId() {
        return eventId;
    }

    @Override
    public String getExchange() {
        return "test.exchange";
    }

    @Override
    public String getRoutingKey() {
        return "test.failing-once";
    }
}
