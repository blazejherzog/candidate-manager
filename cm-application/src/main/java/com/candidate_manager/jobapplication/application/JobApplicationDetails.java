package com.candidate_manager.jobapplication.application;

import com.candidate_manager.jobapplication.domain.JobApplication;
import com.candidate_manager.jobapplication.domain.JobApplicationStatus;

import java.time.Instant;
import java.util.UUID;

public record JobApplicationDetails(
        UUID id,
        UUID jobOfferId,
        String offerTitle,
        String companyName,
        JobApplicationStatus status,
        String message,
        Instant submittedAt,
        Instant updatedAt) {

    static JobApplicationDetails from(JobApplication application) {
        return new JobApplicationDetails(
                application.getId(),
                application.getJobOfferId(),
                application.getOfferTitle(),
                application.getCompanyName(),
                application.getStatus(),
                application.getMessage(),
                application.getSubmittedAt(),
                application.getUpdatedAt());
    }

    @Override
    public String toString() {
        return "JobApplicationDetails[id=%s, status=%s]".formatted(id, status);
    }
}
