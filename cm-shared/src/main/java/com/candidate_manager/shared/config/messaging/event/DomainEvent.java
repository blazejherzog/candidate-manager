package com.candidate_manager.shared.config.messaging.event;

import java.util.UUID;

public interface DomainEvent {
    UUID getEventId();
    String getExchange();
    String getRoutingKey();
}
