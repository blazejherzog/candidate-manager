package com.candidate_manager.auth.application;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class UserAlreadyExistsException extends ErrorResponseException {

    public UserAlreadyExistsException(Throwable cause) {
        super(HttpStatus.CONFLICT,
                ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "User with this username or email already exists"),
                cause);
    }
}
