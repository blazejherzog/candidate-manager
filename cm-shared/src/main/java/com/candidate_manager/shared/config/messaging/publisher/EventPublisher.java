package com.candidate_manager.shared.config.messaging.publisher;

import com.candidate_manager.shared.config.messaging.event.DomainEvent;

public interface EventPublisher {

    void publish(DomainEvent event);
}
