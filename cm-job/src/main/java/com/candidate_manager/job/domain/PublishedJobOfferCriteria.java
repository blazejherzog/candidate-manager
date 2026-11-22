package com.candidate_manager.job.domain;

import java.util.List;

public record PublishedJobOfferCriteria(
        String titleOrCompanyFragment,
        String locationFragment,
        List<JobCategory> categories,
        List<PositionLevel> positionLevels,
        List<WorkMode> workModes,
        List<EmploymentTime> employmentTimes,
        List<ContractType> contractTypes) {

    public PublishedJobOfferCriteria {
        categories = List.copyOf(categories);
        positionLevels = List.copyOf(positionLevels);
        workModes = List.copyOf(workModes);
        employmentTimes = List.copyOf(employmentTimes);
        contractTypes = List.copyOf(contractTypes);
    }
}
