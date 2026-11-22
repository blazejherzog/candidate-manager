package com.candidate_manager.job.web;

import com.candidate_manager.job.domain.JobOfferNotDraftException;
import com.candidate_manager.job.domain.JobOfferValidityExpiredException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = JobOfferController.class)
class JobOfferExceptionHandler {

    private static final String CONCURRENT_MODIFICATION_DETAIL =
            "Job offer was changed by another request; reload it and try again";

    @ExceptionHandler({JobOfferNotDraftException.class, JobOfferValidityExpiredException.class})
    ProblemDetail handleOfferStateConflict(RuntimeException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ProblemDetail handleConcurrentModification() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, CONCURRENT_MODIFICATION_DETAIL);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleInvalidRequest(MethodArgumentNotValidException exception) {
        ProblemDetail problem = exception.getBody();
        problem.setProperty("errors", exception.getBindingResult().getFieldErrors().stream()
                .map(FieldViolation::from)
                .toList());
        return problem;
    }

    record FieldViolation(String field, String message) {

        static FieldViolation from(FieldError error) {
            return new FieldViolation(error.getField(), error.getDefaultMessage());
        }
    }
}
