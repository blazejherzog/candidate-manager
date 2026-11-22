package com.candidate_manager.job.domain;

import java.time.LocalDate;
import java.util.List;

public record JobOfferContent(
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
        LocalDate validUntil) {

    public JobOfferContent {
        locations = List.copyOf(locations);
        salaries = List.copyOf(salaries);
        responsibilities = List.copyOf(responsibilities);
        requirements = List.copyOf(requirements);
        niceToHave = List.copyOf(niceToHave);
        benefits = List.copyOf(benefits);
        languages = List.copyOf(languages);
        recruitmentSteps = List.copyOf(recruitmentSteps);
    }
}
