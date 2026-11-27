package com.candidate_manager.event.infrastructure;

import com.candidate_manager.event.application.ApplicationHistoryService;
import com.candidate_manager.event.domain.ApplicationHistoryEntry;
import com.candidate_manager.shared.config.messaging.consumer.IdempotentConsumer;
import com.candidate_manager.shared.event.JobApplicationStatusChangedEvent;
import com.candidate_manager.shared.event.JobApplicationSubmittedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
class ApplicationHistoryListener {

    static final String SUBMITTED_QUEUE = "cm-event.application-submitted";
    static final String STATUS_CHANGED_QUEUE = "cm-event.application-status-changed";

    private final IdempotentConsumer idempotentConsumer;
    private final ApplicationHistoryService applicationHistoryService;

    @RabbitListener(queues = SUBMITTED_QUEUE)
    void recordSubmission(JobApplicationSubmittedEvent event) {
        recordOnce(event.eventId(), SUBMITTED_QUEUE, ApplicationHistoryEntry.of(event));
    }

    @RabbitListener(queues = STATUS_CHANGED_QUEUE)
    void recordStatusChange(JobApplicationStatusChangedEvent event) {
        recordOnce(event.eventId(), STATUS_CHANGED_QUEUE, ApplicationHistoryEntry.of(event));
    }

    private void recordOnce(UUID eventId, String consumer, ApplicationHistoryEntry entry) {
        boolean processed = idempotentConsumer.runOnce(eventId, consumer, () -> applicationHistoryService.record(entry));
        if (!processed) {
            log.info("Skipped already processed event {}", eventId);
        }
    }
}
