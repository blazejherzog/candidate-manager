package com.candidate_manager.shared.config.messaging;

@RabbitEventHandler(CandidateCreatedEvent.class)
@Idempotent
public class CandidateCreatedHandler implements EventHandler<CandidateCreatedEvent> {

    @Override
    public void handle(CandidateCreatedEvent event) {

    }
}
