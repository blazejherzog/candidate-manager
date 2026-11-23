package com.candidate_manager.jobapplication.domain;

import java.util.UUID;

public class JobApplicationClosedException extends RuntimeException {

    public JobApplicationClosedException(UUID jobApplicationId, JobApplicationStatus status) {
        super("Job application %s is already %s and can no longer be changed".formatted(jobApplicationId, status));
    }
}
