package com.candidate_manager.job.web;

import com.candidate_manager.job.domain.LanguageLevel;
import com.candidate_manager.job.domain.LanguageRequirement;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record LanguageRequirementRequest(
        @NotBlank @Size(max = 50) String language,
        @NotNull LanguageLevel level) {

    LanguageRequirement toLanguageRequirement() {
        return new LanguageRequirement(language, level);
    }
}
