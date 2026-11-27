package com.candidate_manager.event.domain;

import com.candidate_manager.shared.event.JobApplicationActor;
import com.candidate_manager.shared.event.JobApplicationStatusChangedEvent;
import com.candidate_manager.shared.event.JobApplicationSubmittedEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@Table(name = "application_history_entry")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApplicationHistoryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "job_application_id", nullable = false, updatable = false)
    private UUID jobApplicationId;

    @Column(name = "application_version", nullable = false, updatable = false)
    private long applicationVersion;

    @Column(name = "previous_status", updatable = false)
    private String previousStatus;

    @Column(nullable = false, updatable = false)
    private String status;

    @Enumerated(EnumType.STRING)
    @Column(name = "changed_by", nullable = false, updatable = false)
    private JobApplicationActor changedBy;

    @Column(name = "changed_by_user_id", nullable = false, updatable = false)
    private UUID changedByUserId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    public static ApplicationHistoryEntry of(JobApplicationSubmittedEvent event) {
        ApplicationHistoryEntry entry = new ApplicationHistoryEntry();
        entry.jobApplicationId = event.jobApplicationId();
        entry.applicationVersion = event.applicationVersion();
        entry.status = event.status();
        entry.changedBy = JobApplicationActor.CANDIDATE;
        entry.changedByUserId = event.submittedByUserId();
        entry.occurredAt = event.occurredAt();
        return entry;
    }

    public static ApplicationHistoryEntry of(JobApplicationStatusChangedEvent event) {
        ApplicationHistoryEntry entry = new ApplicationHistoryEntry();
        entry.jobApplicationId = event.jobApplicationId();
        entry.applicationVersion = event.applicationVersion();
        entry.previousStatus = event.previousStatus();
        entry.status = event.status();
        entry.changedBy = event.changedBy();
        entry.changedByUserId = event.changedByUserId();
        entry.occurredAt = event.occurredAt();
        return entry;
    }
}
