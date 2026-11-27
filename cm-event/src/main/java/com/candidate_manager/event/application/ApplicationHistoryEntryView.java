package com.candidate_manager.event.application;

import com.candidate_manager.event.domain.ApplicationHistoryEntry;
import com.candidate_manager.shared.event.JobApplicationActor;

import java.time.Instant;

public record ApplicationHistoryEntryView(
        String previousStatus,
        String status,
        JobApplicationActor changedBy,
        Instant occurredAt) {

    static ApplicationHistoryEntryView from(ApplicationHistoryEntry entry) {
        return new ApplicationHistoryEntryView(
                entry.getPreviousStatus(),
                entry.getStatus(),
                entry.getChangedBy(),
                entry.getOccurredAt());
    }
}
