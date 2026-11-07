package com.candidate_manager.messaging;

import com.candidate_manager.shared.config.messaging.event.DomainEvent;

import java.util.UUID;

public record TestOutboxEvent(UUID eventId, String exchange, String routingKey) implements DomainEvent {

    @Override
    public UUID getEventId() {
        return eventId;
    }

    @Override
    public String getExchange() {
        return exchange;
    }

    @Override
    public String getRoutingKey() {
        return routingKey;
    }
}
