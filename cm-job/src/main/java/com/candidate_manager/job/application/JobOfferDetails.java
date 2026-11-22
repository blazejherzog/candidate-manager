package com.candidate_manager.job.application;

import com.candidate_manager.job.domain.EmploymentTime;
import com.candidate_manager.job.domain.JobCategory;
import com.candidate_manager.job.domain.JobOffer;
import com.candidate_manager.job.domain.JobOfferStatus;
import com.candidate_manager.job.domain.LanguageRequirement;
import com.candidate_manager.job.domain.PositionLevel;
import com.candidate_manager.job.domain.Salary;
import com.candidate_manager.job.domain.WorkMode;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record JobOfferDetails(
        UUID id,
        JobOfferStatus status,
        String title,
        String companyName,
        JobCategory category,
        PositionLevel positionLevel,
        WorkMode workMode,
        EmploymentTime employmentTime,
        List<String> locations,
        List<Salary> salaries,
        String description,
        List<String> responsibilities,
        List<String> requirements,
        List<String> niceToHave,
        List<String> benefits,
        List<LanguageRequirement> languages,
        List<String> recruitmentSteps,
        LocalDate startDate,
        LocalDate validUntil,
        Instant createdAt,
        Instant updatedAt,
        Instant publishedAt) {

    static JobOfferDetails from(JobOffer offer) {
        return new JobOfferDetails(
                offer.getId(),
                offer.getStatus(),
                offer.getTitle(),
                offer.getCompanyName(),
                offer.getCategory(),
                offer.getPositionLevel(),
                offer.getWorkMode(),
                offer.getEmploymentTime(),
                List.copyOf(offer.getLocations()),
                offer.getSalaries().stream().sorted(Comparator.comparing(Salary::contractType)).toList(),
                offer.getDescription(),
                List.copyOf(offer.getResponsibilities()),
                List.copyOf(offer.getRequirements()),
                List.copyOf(offer.getNiceToHave()),
                List.copyOf(offer.getBenefits()),
                offer.getLanguages().stream().sorted(Comparator.comparing(LanguageRequirement::language)).toList(),
                List.copyOf(offer.getRecruitmentSteps()),
                offer.getStartDate(),
                offer.getValidUntil(),
                offer.getCreatedAt(),
                offer.getUpdatedAt(),
                offer.getPublishedAt());
    }
}
