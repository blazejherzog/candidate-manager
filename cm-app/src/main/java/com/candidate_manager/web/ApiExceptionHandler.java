package com.candidate_manager.web;

import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.TransactionTimedOutException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.stream.Stream;

@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String CONCURRENT_MODIFICATION_DETAIL =
            "The resource was changed by another request; reload it and try again";
    private static final String QUERY_TIMEOUT_DETAIL = "The request took too long to process; try again or narrow it down";

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ProblemDetail handleConcurrentModification() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, CONCURRENT_MODIFICATION_DETAIL);
    }

    @ExceptionHandler({QueryTimeoutException.class, TransactionTimedOutException.class})
    ProblemDetail handleQueryTimeout() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, QUERY_TIMEOUT_DETAIL);
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

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException exception,
                                                                            HttpHeaders headers,
                                                                            HttpStatusCode status,
                                                                            WebRequest request) {
        ProblemDetail problem = exception.getBody();
        problem.setProperty("errors", exception.getParameterValidationResults().stream()
                .flatMap(FieldViolation::from)
                .toList());
        return handleExceptionInternal(exception, problem, headers, status, request);
    }

    record FieldViolation(String field, String message) {

        static FieldViolation from(ObjectError error) {
            String field = error instanceof FieldError fieldError ? fieldError.getField() : error.getObjectName();
            return new FieldViolation(field, error.getDefaultMessage());
        }

        static Stream<FieldViolation> from(ParameterValidationResult result) {
            if (result instanceof ParameterErrors parameterErrors) {
                return parameterErrors.getAllErrors().stream().map(FieldViolation::from);
            }
            String parameterName = result.getMethodParameter().getParameterName();
            return result.getResolvableErrors().stream()
                    .map(error -> new FieldViolation(parameterName, error.getDefaultMessage()));
        }
    }
}
