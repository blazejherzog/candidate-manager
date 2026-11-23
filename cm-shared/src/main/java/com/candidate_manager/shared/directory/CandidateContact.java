package com.candidate_manager.shared.directory;

import java.util.UUID;

public record CandidateContact(UUID candidateId, String firstName, String lastName, String email, String phoneNumber) {

    @Override
    public String toString() {
        return "CandidateContact[candidateId=%s]".formatted(candidateId);
    }
}
