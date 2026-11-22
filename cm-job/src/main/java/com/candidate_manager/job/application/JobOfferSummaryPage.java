package com.candidate_manager.job.application;

import com.candidate_manager.job.domain.JobOffer;
import org.springframework.data.domain.Page;

import java.time.LocalDate;
import java.util.List;

public record JobOfferSummaryPage(
        List<JobOfferSummary> offers,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    static JobOfferSummaryPage from(Page<JobOffer> offers, LocalDate today) {
        return new JobOfferSummaryPage(
                offers.map(offer -> JobOfferSummary.from(offer, today)).getContent(),
                offers.getNumber(),
                offers.getSize(),
                offers.getTotalElements(),
                offers.getTotalPages());
    }
}
