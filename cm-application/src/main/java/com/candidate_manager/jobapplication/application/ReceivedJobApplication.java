package com.candidate_manager.jobapplication.application;

import com.candidate_manager.jobapplication.domain.JobApplication;
import com.candidate_manager.jobapplication.domain.JobApplicationStatus;
import com.candidate_manager.shared.directory.CandidateContact;

import java.time.Instant;
import java.util.UUID;

public record ReceivedJobApplication(
        UUID id,
        JobApplicationStatus status,
        Applicant applicant,
        Instant submittedAt,
        Instant updatedAt,
        long version) {

    static ReceivedJobApplication from(JobApplication application, CandidateContact contact) {
        return new ReceivedJobApplication(
                application.getId(),
                application.getStatus(),
                Applicant.of(application, contact),
                application.getSubmittedAt(),
                application.getUpdatedAt(),
                application.getVersion());
    }
}
