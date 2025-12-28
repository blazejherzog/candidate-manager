package com.candidate_manager.shared.config.messaging;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ProcessedEventJpaRepository extends
        JpaRepository<ProcessedEventEntity, UUID>,
        ProcessedEventRepository {
}
