package com.candidate_manager.candidate.application;

import com.candidate_manager.candidate.domain.Candidate;

import java.time.Instant;
import java.util.UUID;

public record CandidateProfile(
        UUID id,
        String email,
        String firstName,
        String lastName,
        String phoneNumber,
        String city,
        String headline,
        String summary,
        Instant createdAt,
        Instant updatedAt,
        long version) {

    static CandidateProfile from(Candidate candidate) {
        return new CandidateProfile(
                candidate.getId(),
                candidate.getEmail(),
                candidate.getFirstName(),
                candidate.getLastName(),
                candidate.getPhoneNumber(),
                candidate.getCity(),
                candidate.getHeadline(),
                candidate.getSummary(),
                candidate.getCreatedAt(),
                candidate.getUpdatedAt(),
                candidate.getVersion());
    }

    @Override
    public String toString() {
        return "CandidateProfile[id=%s, version=%d]".formatted(id, version);
    }
}
