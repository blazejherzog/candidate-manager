package com.candidate_manager.shared.config.messaging.listener;

import com.candidate_manager.shared.config.messaging.consumer.IdempotentConsumer;
import com.candidate_manager.shared.config.messaging.event.CandidateCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CandidateCreatedListener {

    private static final String QUEUE = "candidate.created.queue";

    private final IdempotentConsumer idempotentConsumer;

    @RabbitListener(queues = QUEUE)
    public void on(CandidateCreatedEvent event) {
        idempotentConsumer.runOnce(event.eventId(), QUEUE,
                () -> log.info("Received candidate created event {}", event.eventId()));
    }
}
