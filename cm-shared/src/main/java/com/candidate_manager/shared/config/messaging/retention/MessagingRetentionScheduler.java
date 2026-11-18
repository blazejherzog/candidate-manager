package com.candidate_manager.shared.config.messaging.retention;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "messaging.retention.enabled", havingValue = "true", matchIfMissing = true)
public class MessagingRetentionScheduler {

    private final MessagingRetention messagingRetention;

    @Scheduled(fixedDelayString = "${messaging.retention.fixed-delay}")
    void run() {
        messagingRetention.deleteExpiredRows();
    }
}
