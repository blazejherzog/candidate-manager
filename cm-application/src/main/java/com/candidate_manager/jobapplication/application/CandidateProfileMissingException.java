package com.candidate_manager.jobapplication.application;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class CandidateProfileMissingException extends ErrorResponseException {

    private static final String DETAIL = "Candidate profile does not exist yet; it is created shortly after registration";

    public CandidateProfileMissingException() {
        super(HttpStatus.CONFLICT, ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, DETAIL), null);
    }
}
