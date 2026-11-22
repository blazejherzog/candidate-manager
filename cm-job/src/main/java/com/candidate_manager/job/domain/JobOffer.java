package com.candidate_manager.job.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Entity
@Table(name = "job_offer")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobOffer {

    public static final Period DEFAULT_VALIDITY = Period.ofDays(30);
    public static final Period MAX_VALIDITY = Period.ofDays(60);

    @Id
    private UUID id;

    @Column(name = "recruiter_id", nullable = false, updatable = false)
    private UUID recruiterId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobOfferStatus status;

    @Column(nullable = false)
    private String title;

    @Column(name = "company_name", nullable = false)
    private String companyName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "position_level", nullable = false)
    private PositionLevel positionLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_mode", nullable = false)
    private WorkMode workMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_time", nullable = false)
    private EmploymentTime employmentTime;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false)
    private List<String> locations;

    @ElementCollection
    @CollectionTable(name = "job_offer_salary", joinColumns = @JoinColumn(name = "job_offer_id"))
    private List<Salary> salaries = new ArrayList<>();

    @Column(nullable = false, length = 5000)
    private String description;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false)
    private List<String> responsibilities;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false)
    private List<String> requirements;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "nice_to_have", nullable = false)
    private List<String> niceToHave;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false)
    private List<String> benefits;

    @ElementCollection
    @CollectionTable(name = "job_offer_language", joinColumns = @JoinColumn(name = "job_offer_id"))
    private List<LanguageRequirement> languages = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "recruitment_steps", nullable = false)
    private List<String> recruitmentSteps;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "valid_until")
    private LocalDate validUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    public static JobOffer draft(UUID recruiterId, JobOfferContent content, Instant now) {
        JobOffer draft = new JobOffer();
        draft.id = UUID.randomUUID();
        draft.recruiterId = recruiterId;
        draft.status = JobOfferStatus.DRAFT;
        draft.createdAt = now;
        draft.updatedAt = now;
        draft.applyContent(content);
        return draft;
    }

    public void updateDraft(JobOfferContent content, Instant now) {
        requireDraft();
        applyContent(content);
        updatedAt = now;
    }

    public void publish(LocalDate today, Instant now) {
        requireDraft();
        if (validUntil == null) {
            validUntil = today.plus(DEFAULT_VALIDITY);
        } else if (!validUntil.isAfter(today)) {
            throw new JobOfferValidityExpiredException(id, validUntil);
        }
        status = JobOfferStatus.PUBLISHED;
        publishedAt = now;
        updatedAt = now;
    }

    public void requireDraft() {
        if (status != JobOfferStatus.DRAFT) {
            throw new JobOfferNotDraftException(id);
        }
    }

    public boolean isVisibleTo(UUID userId) {
        return status == JobOfferStatus.PUBLISHED || recruiterId.equals(userId);
    }

    private void applyContent(JobOfferContent content) {
        title = content.title();
        companyName = content.companyName();
        category = content.category();
        positionLevel = content.positionLevel();
        workMode = content.workMode();
        employmentTime = content.employmentTime();
        locations = new ArrayList<>(content.locations());
        salaries.clear();
        salaries.addAll(content.salaries());
        description = content.description();
        responsibilities = new ArrayList<>(content.responsibilities());
        requirements = new ArrayList<>(content.requirements());
        niceToHave = new ArrayList<>(content.niceToHave());
        benefits = new ArrayList<>(content.benefits());
        languages.clear();
        languages.addAll(content.languages());
        recruitmentSteps = new ArrayList<>(content.recruitmentSteps());
        startDate = content.startDate();
        validUntil = content.validUntil();
    }
}
