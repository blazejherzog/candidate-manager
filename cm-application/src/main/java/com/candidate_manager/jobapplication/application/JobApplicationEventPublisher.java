package com.candidate_manager.jobapplication.application;

import com.candidate_manager.jobapplication.domain.JobApplication;
import com.candidate_manager.jobapplication.domain.JobApplicationStatus;
import com.candidate_manager.shared.config.messaging.publisher.EventPublisher;
import com.candidate_manager.shared.event.JobApplicationActor;
import com.candidate_manager.shared.event.JobApplicationStatusChangedEvent;
import com.candidate_manager.shared.event.JobApplicationSubmittedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
class JobApplicationEventPublisher {

    private final EventPublisher eventPublisher;

    void publishSubmitted(JobApplication application, UUID submittedByUserId) {
        eventPublisher.publish(new JobApplicationSubmittedEvent(
                UUID.randomUUID(),
                application.getSubmittedAt(),
                application.getId(),
                application.getVersion(),
                application.getJobOfferId(),
                application.getCandidateId(),
                application.getRecruiterId(),
                application.getStatus().name(),
                submittedByUserId));
    }

    void publishStatusChanged(JobApplication application, JobApplicationStatus previousStatus,
                              JobApplicationActor changedBy, UUID changedByUserId) {
        eventPublisher.publish(new JobApplicationStatusChangedEvent(
                UUID.randomUUID(),
                application.getUpdatedAt(),
                application.getId(),
                application.getVersion(),
                application.getJobOfferId(),
                application.getCandidateId(),
                application.getRecruiterId(),
                previousStatus.name(),
                application.getStatus().name(),
                changedBy,
                changedByUserId));
    }
}
