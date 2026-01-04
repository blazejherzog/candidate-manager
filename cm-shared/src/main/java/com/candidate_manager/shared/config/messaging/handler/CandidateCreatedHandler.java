package com.candidate_manager.shared.config.messaging.handler;

import com.candidate_manager.shared.config.messaging.event.CandidateCreatedEvent;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RabbitEventHandler(CandidateCreatedEvent.class)
@Idempotent
public class CandidateCreatedHandler implements EventHandler<CandidateCreatedEvent> {

    @Override
    public void handle(CandidateCreatedEvent event) {
        log.info("Received candidate created event: {}", event);
    }
}
