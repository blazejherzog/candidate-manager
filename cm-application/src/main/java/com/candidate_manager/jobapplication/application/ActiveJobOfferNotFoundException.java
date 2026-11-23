package com.candidate_manager.jobapplication.application;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class ActiveJobOfferNotFoundException extends ErrorResponseException {

    private static final String DETAIL = "Job offer not found";

    public ActiveJobOfferNotFoundException() {
        super(HttpStatus.NOT_FOUND, ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, DETAIL), null);
    }
}
