package com.candidate_manager.auth.application;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class IdentityProviderException extends ErrorResponseException {

    public IdentityProviderException(Throwable cause) {
        super(HttpStatus.BAD_GATEWAY,
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, "Identity provider request failed"),
                cause);
    }
}
