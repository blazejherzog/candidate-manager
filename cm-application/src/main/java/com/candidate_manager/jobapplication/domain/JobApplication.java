package com.candidate_manager.jobapplication.domain;

import com.candidate_manager.shared.directory.ActiveJobOffer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@Table(name = "job_application")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobApplication {

    public static final String ONE_PER_CANDIDATE_AND_OFFER_CONSTRAINT = "job_application_one_per_candidate_and_offer";

    @Id
    private UUID id;

    @Column(name = "job_offer_id", nullable = false, updatable = false)
    private UUID jobOfferId;

    @Column(name = "candidate_id", nullable = false, updatable = false)
    private UUID candidateId;

    @Column(name = "recruiter_id", nullable = false, updatable = false)
    private UUID recruiterId;

    @Column(name = "offer_title", nullable = false, updatable = false)
    private String offerTitle;

    @Column(name = "company_name", nullable = false, updatable = false)
    private String companyName;

    @Column(updatable = false)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobApplicationStatus status;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    public static JobApplication submit(ActiveJobOffer offer, UUID candidateId, String message, Instant now) {
        JobApplication application = new JobApplication();
        application.id = UUID.randomUUID();
        application.jobOfferId = offer.id();
        application.candidateId = candidateId;
        application.recruiterId = offer.recruiterId();
        application.offerTitle = offer.title();
        application.companyName = offer.companyName();
        application.message = message;
        application.status = JobApplicationStatus.SUBMITTED;
        application.submittedAt = now;
        application.updatedAt = now;
        return application;
    }

    public void withdraw(Instant now) {
        requireOpen();
        status = JobApplicationStatus.WITHDRAWN;
        updatedAt = now;
    }

    private void requireOpen() {
        if (status.isFinal()) {
            throw new JobApplicationClosedException(id, status);
        }
    }
}
