package com.candidate_manager.jobapplication.application;

import com.candidate_manager.jobapplication.domain.JobApplication;
import com.candidate_manager.jobapplication.domain.JobApplicationStatus;
import com.candidate_manager.shared.directory.CandidateContact;

import java.time.Instant;
import java.util.UUID;

public record JobApplicationDetails(
        UUID id,
        UUID jobOfferId,
        String offerTitle,
        String companyName,
        JobApplicationStatus status,
        String message,
        Applicant applicant,
        Instant submittedAt,
        Instant updatedAt,
        long version) {

    static JobApplicationDetails forCandidate(JobApplication application) {
        return from(application, application.getMessage(), null);
    }

    static JobApplicationDetails forRecruiter(JobApplication application, CandidateContact contact) {
        String visibleMessage = application.isWithdrawn() ? null : application.getMessage();
        return from(application, visibleMessage, Applicant.of(application, contact));
    }

    private static JobApplicationDetails from(JobApplication application, String visibleMessage, Applicant applicant) {
        return new JobApplicationDetails(
                application.getId(),
                application.getJobOfferId(),
                application.getOfferTitle(),
                application.getCompanyName(),
                application.getStatus(),
                visibleMessage,
                applicant,
                application.getSubmittedAt(),
                application.getUpdatedAt(),
                application.getVersion());
    }

    @Override
    public String toString() {
        return "JobApplicationDetails[id=%s, status=%s]".formatted(id, status);
    }
}
