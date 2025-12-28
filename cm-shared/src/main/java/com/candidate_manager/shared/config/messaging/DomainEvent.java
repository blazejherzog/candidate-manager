package com.candidate_manager.shared.config.messaging;

import java.util.UUID;

public interface DomainEvent {
    UUID getEventId();
}
