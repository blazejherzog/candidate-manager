package com.candidate_manager.event.application;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class JobApplicationNotVisibleException extends ErrorResponseException {

    private static final String DETAIL = "Job application not found";

    public JobApplicationNotVisibleException() {
        super(HttpStatus.NOT_FOUND, ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, DETAIL), null);
    }
}
