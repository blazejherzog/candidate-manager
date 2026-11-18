package com.candidate_manager.shared.config.messaging.consumer;

import com.candidate_manager.shared.config.messaging.processed.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class IdempotentConsumer {

    private final ProcessedEventRepository processedEventRepository;

    @Transactional
    public boolean runOnce(UUID eventId, String consumer, Runnable effect) {
        if (!processedEventRepository.tryMarkAsProcessed(eventId, consumer)) {
            return false;
        }
        effect.run();
        return true;
    }
}
