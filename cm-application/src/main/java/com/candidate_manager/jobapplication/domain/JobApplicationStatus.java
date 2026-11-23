package com.candidate_manager.jobapplication.domain;

import java.util.Set;

public enum JobApplicationStatus {
    SUBMITTED,
    IN_REVIEW,
    INTERVIEW,
    HIRED,
    REJECTED,
    WITHDRAWN;

    public boolean isFinal() {
        return statusesSelectableByRecruiter().isEmpty();
    }

    public boolean canBeChangedByRecruiterTo(JobApplicationStatus newStatus) {
        return statusesSelectableByRecruiter().contains(newStatus);
    }

    private Set<JobApplicationStatus> statusesSelectableByRecruiter() {
        return switch (this) {
            case SUBMITTED -> Set.of(IN_REVIEW, INTERVIEW, HIRED, REJECTED);
            case IN_REVIEW -> Set.of(INTERVIEW, HIRED, REJECTED);
            case INTERVIEW -> Set.of(HIRED, REJECTED);
            case HIRED, REJECTED, WITHDRAWN -> Set.of();
        };
    }
}
