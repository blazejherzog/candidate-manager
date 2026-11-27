package com.candidate_manager.auth.infrastructure;

import com.candidate_manager.auth.application.IncompleteRegistrationCleanup;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "registration.cleanup.enabled", havingValue = "true", matchIfMissing = true)
class IncompleteRegistrationCleanupScheduler {

    private final IncompleteRegistrationCleanup incompleteRegistrationCleanup;

    @Scheduled(fixedDelayString = "${registration.cleanup.fixed-delay}")
    void run() {
        incompleteRegistrationCleanup.removeStaleRegistrations();
    }
}
