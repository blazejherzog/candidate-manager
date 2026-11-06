package com.candidate_manager.shared.config.messaging.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelayScheduler {

    private final OutboxRelay outboxRelay;

    @Scheduled(fixedDelayString = "${outbox.relay.fixed-delay}")
    void run() {
        outboxRelay.publishPendingEvents();
    }
}
