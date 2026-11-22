package com.candidate_manager.web;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String CONCURRENT_MODIFICATION_DETAIL =
            "The resource was changed by another request; reload it and try again";

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ProblemDetail handleConcurrentModification() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, CONCURRENT_MODIFICATION_DETAIL);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        ProblemDetail problem = exception.getBody();
        problem.setProperty("errors", exception.getBindingResult().getAllErrors().stream()
                .map(FieldViolation::from)
                .toList());
        return handleExceptionInternal(exception, problem, headers, status, request);
    }

    record FieldViolation(String field, String message) {

        static FieldViolation from(ObjectError error) {
            String field = error instanceof FieldError fieldError ? fieldError.getField() : error.getObjectName();
            return new FieldViolation(field, error.getDefaultMessage());
        }
    }
}
