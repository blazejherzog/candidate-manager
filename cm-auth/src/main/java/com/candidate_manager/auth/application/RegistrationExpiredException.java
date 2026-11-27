package com.candidate_manager.auth.application;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class RegistrationExpiredException extends ErrorResponseException {

    private static final String DETAIL = "Registration took too long and was cancelled; try again";

    public RegistrationExpiredException() {
        super(HttpStatus.SERVICE_UNAVAILABLE, ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, DETAIL), null);
    }
}
