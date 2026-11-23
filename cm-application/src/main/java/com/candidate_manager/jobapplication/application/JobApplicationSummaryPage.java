package com.candidate_manager.jobapplication.application;

import com.candidate_manager.jobapplication.domain.JobApplication;
import org.springframework.data.domain.Page;

import java.util.List;

public record JobApplicationSummaryPage(
        List<JobApplicationSummary> applications,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    static JobApplicationSummaryPage from(Page<JobApplication> applications) {
        return new JobApplicationSummaryPage(
                applications.map(JobApplicationSummary::from).getContent(),
                applications.getNumber(),
                applications.getSize(),
                applications.getTotalElements(),
                applications.getTotalPages());
    }
}
