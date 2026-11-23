package com.candidate_manager.jobapplication.application;

import com.candidate_manager.jobapplication.domain.JobApplication;
import com.candidate_manager.shared.directory.CandidateContact;

import java.util.UUID;

public record Applicant(UUID candidateId, String firstName, String lastName, String email, String phoneNumber) {

    static Applicant of(JobApplication application, CandidateContact contact) {
        return application.isWithdrawn()
                ? new Applicant(contact.candidateId(), contact.firstName(), contact.lastName(), null, null)
                : new Applicant(contact.candidateId(), contact.firstName(), contact.lastName(), contact.email(), contact.phoneNumber());
    }

    @Override
    public String toString() {
        return "Applicant[candidateId=%s]".formatted(candidateId);
    }
}
