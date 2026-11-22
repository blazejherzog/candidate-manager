package com.candidate_manager.job.web;

import com.candidate_manager.job.domain.JobOfferNotDraftException;
import com.candidate_manager.job.domain.JobOfferValidityExpiredException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = JobOfferController.class)
class JobOfferExceptionHandler {

    @ExceptionHandler({JobOfferNotDraftException.class, JobOfferValidityExpiredException.class})
    ProblemDetail handleOfferStateConflict(RuntimeException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }
}
