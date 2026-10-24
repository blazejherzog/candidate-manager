package com.candidate_manager.shared.config.messaging.processed;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ProcessedEventRepositoryImpl implements ProcessedEventRepository {

    private final ProcessedEventJpaRepository processedEventJpaRepository;

    @Override
    public boolean tryMarkAsProcessed(UUID eventId) {
        return processedEventJpaRepository.insertProcessedEvent(eventId) == 1;
    }
}
