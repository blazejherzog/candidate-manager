package com.candidate_manager.job.web;

import com.candidate_manager.job.domain.ContractType;
import com.candidate_manager.job.domain.EmploymentTime;
import com.candidate_manager.job.domain.JobCategory;
import com.candidate_manager.job.domain.PositionLevel;
import com.candidate_manager.job.domain.PublishedJobOfferCriteria;
import com.candidate_manager.job.domain.WorkMode;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Objects;

record PublishedJobOfferSearchRequest(
        @Size(min = MIN_SEARCH_FRAGMENT_LENGTH, max = MAX_SEARCH_FRAGMENT_LENGTH) String keyword,
        @Size(min = MIN_SEARCH_FRAGMENT_LENGTH, max = MAX_SEARCH_FRAGMENT_LENGTH) String location,
        List<JobCategory> category,
        List<PositionLevel> positionLevel,
        List<WorkMode> workMode,
        List<EmploymentTime> employmentTime,
        List<ContractType> contractType) {

    static final int MIN_SEARCH_FRAGMENT_LENGTH = 3;
    static final int MAX_SEARCH_FRAGMENT_LENGTH = 100;

    PublishedJobOfferSearchRequest {
        keyword = strippedOrNullIfBlank(keyword);
        location = strippedOrNullIfBlank(location);
        category = withoutDuplicatesOrNulls(category);
        positionLevel = withoutDuplicatesOrNulls(positionLevel);
        workMode = withoutDuplicatesOrNulls(workMode);
        employmentTime = withoutDuplicatesOrNulls(employmentTime);
        contractType = withoutDuplicatesOrNulls(contractType);
    }

    PublishedJobOfferCriteria toCriteria() {
        return new PublishedJobOfferCriteria(keyword, location, category, positionLevel, workMode, employmentTime, contractType);
    }

    private static String strippedOrNullIfBlank(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static <T> List<T> withoutDuplicatesOrNulls(List<T> values) {
        return values == null ? List.of() : values.stream().filter(Objects::nonNull).distinct().toList();
    }
}
