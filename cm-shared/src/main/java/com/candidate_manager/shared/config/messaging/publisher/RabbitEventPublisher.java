package com.candidate_manager.shared.config.messaging.publisher;

import com.candidate_manager.shared.config.messaging.event.DomainEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RabbitEventPublisher implements EventPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Override
    public void publish(DomainEvent event) {
        log.info("Publishing event: {}", event);
        rabbitTemplate.convertAndSend(event.getExchange(), event.getRoutingKey(), event);
    }
}
