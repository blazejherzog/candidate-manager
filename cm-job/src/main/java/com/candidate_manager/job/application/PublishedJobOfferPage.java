package com.candidate_manager.job.application;

import com.candidate_manager.job.domain.JobOffer;
import org.springframework.data.domain.Page;

import java.util.List;

public record PublishedJobOfferPage(
        List<PublishedJobOfferSummary> offers,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    static PublishedJobOfferPage from(Page<JobOffer> offers) {
        return new PublishedJobOfferPage(
                offers.map(PublishedJobOfferSummary::from).getContent(),
                offers.getNumber(),
                offers.getSize(),
                offers.getTotalElements(),
                offers.getTotalPages());
    }
}
