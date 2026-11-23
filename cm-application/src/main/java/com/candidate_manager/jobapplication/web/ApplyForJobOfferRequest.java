package com.candidate_manager.jobapplication.web;

import jakarta.validation.constraints.Size;

record ApplyForJobOfferRequest(@Size(max = 2000) String message) {

    ApplyForJobOfferRequest {
        message = message == null || message.isBlank() ? null : message.strip();
    }

    @Override
    public String toString() {
        return "ApplyForJobOfferRequest[***]";
    }
}
