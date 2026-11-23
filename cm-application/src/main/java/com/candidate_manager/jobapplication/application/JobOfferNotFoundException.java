package com.candidate_manager.jobapplication.application;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class JobOfferNotFoundException extends ErrorResponseException {

    private static final String DETAIL = "Job offer not found";

    public JobOfferNotFoundException() {
        super(HttpStatus.NOT_FOUND, ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, DETAIL), null);
    }
}
