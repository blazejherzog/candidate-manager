package com.candidate_manager.candidate.application;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class CandidateProfileNotFoundException extends ErrorResponseException {

    private static final String DETAIL = "Candidate profile not found; it is created shortly after registration";

    public CandidateProfileNotFoundException() {
        super(HttpStatus.NOT_FOUND, ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, DETAIL), null);
    }
}
