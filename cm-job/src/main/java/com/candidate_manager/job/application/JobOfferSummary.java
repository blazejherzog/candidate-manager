package com.candidate_manager.job.application;

import com.candidate_manager.job.domain.JobCategory;
import com.candidate_manager.job.domain.JobOffer;
import com.candidate_manager.job.domain.JobOfferStatus;
import com.candidate_manager.job.domain.PositionLevel;
import com.candidate_manager.job.domain.WorkMode;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record JobOfferSummary(
        UUID id,
        JobOfferStatus status,
        String title,
        String companyName,
        JobCategory category,
        PositionLevel positionLevel,
        WorkMode workMode,
        LocalDate validUntil,
        Instant createdAt,
        Instant publishedAt) {

    static JobOfferSummary from(JobOffer offer) {
        return new JobOfferSummary(
                offer.getId(),
                offer.getStatus(),
                offer.getTitle(),
                offer.getCompanyName(),
                offer.getCategory(),
                offer.getPositionLevel(),
                offer.getWorkMode(),
                offer.getValidUntil(),
                offer.getCreatedAt(),
                offer.getPublishedAt());
    }
}
