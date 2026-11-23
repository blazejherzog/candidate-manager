package com.candidate_manager.jobapplication.web;

import com.candidate_manager.jobapplication.domain.JobApplicationStatus;
import jakarta.validation.constraints.NotNull;

record ChangeJobApplicationStatusRequest(@NotNull JobApplicationStatus status, @NotNull Long version) {
}
