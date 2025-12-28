package com.candidate_manager.shared.config.messaging;

import java.util.UUID;

public interface ProcessedEventRepository {
    boolean existsByEventId(UUID eventId);
    void save(UUID eventId);
}
