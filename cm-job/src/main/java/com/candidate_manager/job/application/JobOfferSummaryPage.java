package com.candidate_manager.job.application;

import com.candidate_manager.job.domain.JobOffer;
import org.springframework.data.domain.Page;

import java.util.List;

public record JobOfferSummaryPage(
        List<JobOfferSummary> offers,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    static JobOfferSummaryPage from(Page<JobOffer> offers) {
        return new JobOfferSummaryPage(
                offers.map(JobOfferSummary::from).getContent(),
                offers.getNumber(),
                offers.getSize(),
                offers.getTotalElements(),
                offers.getTotalPages());
    }
}
