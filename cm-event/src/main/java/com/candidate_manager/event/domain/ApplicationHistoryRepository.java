package com.candidate_manager.event.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ApplicationHistoryRepository extends JpaRepository<ApplicationHistoryEntry, UUID> {

    List<ApplicationHistoryEntry> findByJobApplicationIdOrderByApplicationVersion(UUID jobApplicationId);
}
