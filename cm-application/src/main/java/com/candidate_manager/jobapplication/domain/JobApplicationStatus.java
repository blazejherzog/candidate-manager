package com.candidate_manager.jobapplication.domain;

public enum JobApplicationStatus {
    SUBMITTED,
    IN_REVIEW,
    INTERVIEW,
    HIRED,
    REJECTED,
    WITHDRAWN;

    public boolean isFinal() {
        return this == HIRED || this == REJECTED || this == WITHDRAWN;
    }
}
