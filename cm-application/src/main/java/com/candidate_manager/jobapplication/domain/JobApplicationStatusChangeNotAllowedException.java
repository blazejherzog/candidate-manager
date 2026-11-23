package com.candidate_manager.jobapplication.domain;

import java.util.UUID;

public class JobApplicationStatusChangeNotAllowedException extends RuntimeException {

    public JobApplicationStatusChangeNotAllowedException(UUID jobApplicationId, JobApplicationStatus currentStatus,
                                                         JobApplicationStatus newStatus) {
        super("Job application %s cannot be changed from %s to %s".formatted(jobApplicationId, currentStatus, newStatus));
    }
}
