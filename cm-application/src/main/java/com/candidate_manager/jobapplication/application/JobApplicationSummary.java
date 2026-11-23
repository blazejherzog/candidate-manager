package com.candidate_manager.jobapplication.application;

import com.candidate_manager.jobapplication.domain.JobApplication;
import com.candidate_manager.jobapplication.domain.JobApplicationStatus;

import java.time.Instant;
import java.util.UUID;

public record JobApplicationSummary(
        UUID id,
        UUID jobOfferId,
        String offerTitle,
        String companyName,
        JobApplicationStatus status,
        Instant submittedAt,
        Instant updatedAt) {

    static JobApplicationSummary from(JobApplication application) {
        return new JobApplicationSummary(
                application.getId(),
                application.getJobOfferId(),
                application.getOfferTitle(),
                application.getCompanyName(),
                application.getStatus(),
                application.getSubmittedAt(),
                application.getUpdatedAt());
    }
}
