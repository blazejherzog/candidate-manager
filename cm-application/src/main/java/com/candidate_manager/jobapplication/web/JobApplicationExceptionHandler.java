package com.candidate_manager.jobapplication.web;

import com.candidate_manager.jobapplication.domain.JobApplicationClosedException;
import com.candidate_manager.jobapplication.domain.JobApplicationStatusChangeNotAllowedException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = JobApplicationController.class)
class JobApplicationExceptionHandler {

    @ExceptionHandler({JobApplicationClosedException.class, JobApplicationStatusChangeNotAllowedException.class})
    ProblemDetail handleApplicationStateConflict(RuntimeException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }
}
