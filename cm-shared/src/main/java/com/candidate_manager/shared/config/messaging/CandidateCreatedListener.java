package com.candidate_manager.shared.config.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CandidateCreatedListener {

    private final RabbitEventDispatcher eventDispatcher;

    @RabbitListener(
            queues = "candidate.created.queue",
            containerFactory = "rabbitListenerContainerFactory"
    )
    public void on(CandidateCreatedEvent event) {
        eventDispatcher.dispatch(event);
    }
}
