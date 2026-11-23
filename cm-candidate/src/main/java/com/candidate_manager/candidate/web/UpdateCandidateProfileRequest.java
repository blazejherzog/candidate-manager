package com.candidate_manager.candidate.web;

import com.candidate_manager.candidate.domain.CandidateDetails;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

record UpdateCandidateProfileRequest(
        @NotBlank @Size(max = 255) String firstName,
        @NotBlank @Size(max = 255) String lastName,
        @Pattern(regexp = E164_PHONE_NUMBER, message = "must be in E.164 format, e.g. +48123456789") String phoneNumber,
        @Size(max = 100) String city,
        @Size(max = 120) String headline,
        @Size(max = 2000) String summary,
        @NotNull Long version) {

    static final String E164_PHONE_NUMBER = "\\+[1-9][0-9]{7,14}";

    UpdateCandidateProfileRequest {
        firstName = strippedOrNullIfBlank(firstName);
        lastName = strippedOrNullIfBlank(lastName);
        phoneNumber = strippedOrNullIfBlank(phoneNumber);
        city = strippedOrNullIfBlank(city);
        headline = strippedOrNullIfBlank(headline);
        summary = strippedOrNullIfBlank(summary);
    }

    CandidateDetails toDetails() {
        return new CandidateDetails(firstName, lastName, phoneNumber, city, headline, summary);
    }

    private static String strippedOrNullIfBlank(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    @Override
    public String toString() {
        return "UpdateCandidateProfileRequest[version=%d]".formatted(version);
    }
}
