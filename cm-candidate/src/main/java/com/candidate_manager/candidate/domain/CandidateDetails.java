package com.candidate_manager.candidate.domain;

public record CandidateDetails(
        String firstName,
        String lastName,
        String phoneNumber,
        String city,
        String headline,
        String summary) {

    @Override
    public String toString() {
        return "CandidateDetails[***]";
    }
}
