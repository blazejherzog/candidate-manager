package com.candidate_manager.shared.config.messaging.publisher;

import com.candidate_manager.shared.config.messaging.event.DomainEvent;
import com.candidate_manager.shared.config.messaging.outbox.OutboxEventJpaRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventPublisher implements EventPublisher {

    private final OutboxEventJpaRepository outboxEventJpaRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(DomainEvent event) {
        outboxEventJpaRepository.insert(event.getEventId(),
                event.getClass().getName(),
                event.getExchange(),
                event.getRoutingKey(),
                toJson(event));

        log.info("Stored outbox event {} of type {}", event.getEventId(), event.getClass().getSimpleName());
    }

    private String toJson(DomainEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize outbox event %s".formatted(event.getEventId()), e);
        }
    }
}
