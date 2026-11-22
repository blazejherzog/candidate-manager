package com.candidate_manager.job.application;

import com.candidate_manager.job.domain.EmploymentTime;
import com.candidate_manager.job.domain.JobCategory;
import com.candidate_manager.job.domain.JobOffer;
import com.candidate_manager.job.domain.PositionLevel;
import com.candidate_manager.job.domain.Salary;
import com.candidate_manager.job.domain.WorkMode;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record PublishedJobOfferSummary(
        UUID id,
        String title,
        String companyName,
        JobCategory category,
        PositionLevel positionLevel,
        WorkMode workMode,
        EmploymentTime employmentTime,
        List<String> locations,
        List<Salary> salaries,
        Instant publishedAt,
        LocalDate validUntil) {

    static PublishedJobOfferSummary from(JobOffer offer) {
        return new PublishedJobOfferSummary(
                offer.getId(),
                offer.getTitle(),
                offer.getCompanyName(),
                offer.getCategory(),
                offer.getPositionLevel(),
                offer.getWorkMode(),
                offer.getEmploymentTime(),
                List.copyOf(offer.getLocations()),
                offer.getSalaries().stream().sorted(Comparator.comparing(Salary::contractType)).toList(),
                offer.getPublishedAt(),
                offer.getValidUntil());
    }
}
