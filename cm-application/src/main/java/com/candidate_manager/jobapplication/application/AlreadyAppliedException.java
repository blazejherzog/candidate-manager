package com.candidate_manager.jobapplication.application;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class AlreadyAppliedException extends ErrorResponseException {

    private static final String DETAIL = "You have already applied for this job offer";

    public AlreadyAppliedException() {
        super(HttpStatus.CONFLICT, ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, DETAIL), null);
    }
}
