package com.candidate_manager.candidate.infrastructure;

import com.candidate_manager.candidate.application.CandidateProfileService;
import com.candidate_manager.shared.config.messaging.consumer.IdempotentConsumer;
import com.candidate_manager.shared.event.CandidateRegisteredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class CandidateRegisteredListener {

    static final String QUEUE = "cm-candidate.candidate-registered";

    private final IdempotentConsumer idempotentConsumer;
    private final CandidateProfileService candidateProfileService;

    @RabbitListener(queues = QUEUE)
    void createProfileForRegisteredCandidate(CandidateRegisteredEvent event) {
        boolean processed = idempotentConsumer.runOnce(event.eventId(), QUEUE, () ->
                candidateProfileService.createProfile(event.userId(), event.email(), event.firstName(), event.lastName()));
        if (!processed) {
            log.info("Skipped already processed event {}", event.eventId());
        }
    }
}
