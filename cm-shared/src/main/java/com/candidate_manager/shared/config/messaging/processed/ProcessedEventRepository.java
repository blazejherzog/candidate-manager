package com.candidate_manager.shared.config.messaging.processed;

import java.util.UUID;

public interface ProcessedEventRepository {
    boolean exists(UUID eventId);
    void markAsProcessed(UUID eventId);
}
