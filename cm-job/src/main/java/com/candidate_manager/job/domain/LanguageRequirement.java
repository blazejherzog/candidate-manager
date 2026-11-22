package com.candidate_manager.job.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Embeddable
public record LanguageRequirement(
        @Column(name = "language", nullable = false) String language,
        @Enumerated(EnumType.STRING) @Column(name = "level", nullable = false) LanguageLevel level) {
}
