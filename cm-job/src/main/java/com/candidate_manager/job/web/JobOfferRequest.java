package com.candidate_manager.job.web;

import com.candidate_manager.job.domain.EmploymentTime;
import com.candidate_manager.job.domain.JobCategory;
import com.candidate_manager.job.domain.JobOfferContent;
import com.candidate_manager.job.domain.PositionLevel;
import com.candidate_manager.job.domain.WorkMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public record JobOfferRequest(
        @NotBlank @Size(min = 5, max = 120) String title,
        @NotBlank @Size(max = 200) String companyName,
        @NotNull JobCategory category,
        @NotNull PositionLevel positionLevel,
        @NotNull WorkMode workMode,
        EmploymentTime employmentTime,
        @Size(max = 20) List<@NotBlank @Size(max = 100) String> locations,
        @NotEmpty @Size(max = 6) List<@NotNull @Valid SalaryRequest> salaries,
        @NotBlank @Size(max = 5000) String description,
        @NotEmpty @Size(max = 30) List<@NotBlank @Size(max = 500) String> responsibilities,
        @NotEmpty @Size(max = 30) List<@NotBlank @Size(max = 500) String> requirements,
        @Size(max = 30) List<@NotBlank @Size(max = 500) String> niceToHave,
        @Size(max = 30) List<@NotBlank @Size(max = 500) String> benefits,
        @Size(max = 10) List<@NotNull @Valid LanguageRequirementRequest> languages,
        @Size(max = 10) List<@NotBlank @Size(max = 500) String> recruitmentSteps,
        @FutureOrPresent LocalDate startDate,
        @Future @WithinMaxValidity LocalDate validUntil) {

    @AssertTrue(message = "locations are required unless workMode is REMOTE")
    boolean isLocationGivenUnlessRemote() {
        return workMode == null || workMode == WorkMode.REMOTE || !orEmpty(locations).isEmpty();
    }

    @AssertTrue(message = "each contractType may be listed only once")
    boolean isEachContractTypeListedOnce() {
        return hasNoDuplicates(salaries, SalaryRequest::contractType);
    }

    @AssertTrue(message = "each language may be listed only once")
    boolean isEachLanguageListedOnce() {
        return hasNoDuplicates(languages, LanguageRequirementRequest::language);
    }

    JobOfferContent toContent() {
        return new JobOfferContent(
                title,
                companyName,
                category,
                positionLevel,
                workMode,
                Objects.requireNonNullElse(employmentTime, EmploymentTime.FULL_TIME),
                orEmpty(locations),
                salaries.stream().map(SalaryRequest::toSalary).toList(),
                description,
                responsibilities,
                requirements,
                orEmpty(niceToHave),
                orEmpty(benefits),
                orEmpty(languages).stream().map(LanguageRequirementRequest::toLanguageRequirement).toList(),
                orEmpty(recruitmentSteps),
                startDate,
                validUntil);
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static <T, K> boolean hasNoDuplicates(List<T> items, Function<T, K> key) {
        List<K> keys = orEmpty(items).stream().filter(Objects::nonNull).map(key).filter(Objects::nonNull).toList();
        return keys.stream().distinct().count() == keys.size();
    }
}
