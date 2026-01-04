package com.candidate_manager.shared.config.messaging.processed;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ProcessedEventRepositoryImpl implements ProcessedEventRepository {

    private final ProcessedEventJpaRepository processedEventJpaRepository;

    @Override
    public boolean exists(UUID eventId) {
        return processedEventJpaRepository.existsByEventId(eventId);
    }

    @Override
    public void markAsProcessed(UUID eventId) {
        processedEventJpaRepository.save(new ProcessedEventEntity(eventId));
    }
}
