package com.candidate_manager.jobapplication;

import com.candidate_manager.candidate.application.CandidateProfileService;
import com.candidate_manager.job.application.JobOfferService;
import com.candidate_manager.job.domain.ContractType;
import com.candidate_manager.job.domain.EmploymentTime;
import com.candidate_manager.job.domain.JobCategory;
import com.candidate_manager.job.domain.JobOfferContent;
import com.candidate_manager.job.domain.PositionLevel;
import com.candidate_manager.job.domain.Salary;
import com.candidate_manager.job.domain.SalaryCurrency;
import com.candidate_manager.job.domain.SalaryPeriod;
import com.candidate_manager.job.domain.WorkMode;
import org.springframework.boot.test.context.TestComponent;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@TestComponent
public class JobApplicationTestData {

    public static final String OFFER_TITLE = "Senior Java Developer";
    public static final String COMPANY_NAME = "Acme Sp. z o.o.";

    private final JobOfferService jobOfferService;
    private final CandidateProfileService candidateProfileService;

    public JobApplicationTestData(JobOfferService jobOfferService, CandidateProfileService candidateProfileService) {
        this.jobOfferService = jobOfferService;
        this.candidateProfileService = candidateProfileService;
    }

    public UUID registeredCandidate() {
        UUID userId = UUID.randomUUID();
        candidateProfileService.createProfile(userId, userId + "@example.com", "Jan", "Kowalski");
        return userId;
    }

    public UUID draftOfferOf(UUID recruiterId) {
        return jobOfferService.createDraft(recruiterId, offerContent()).id();
    }

    public UUID publishedOfferOf(UUID recruiterId) {
        UUID offerId = draftOfferOf(recruiterId);
        jobOfferService.publish(offerId, recruiterId);
        return offerId;
    }

    public UUID publishedOffer() {
        return publishedOfferOf(UUID.randomUUID());
    }

    private static JobOfferContent offerContent() {
        return new JobOfferContent(
                OFFER_TITLE,
                COMPANY_NAME,
                JobCategory.IT,
                PositionLevel.SENIOR,
                WorkMode.REMOTE,
                EmploymentTime.FULL_TIME,
                List.of(),
                List.of(new Salary(ContractType.B2B, new BigDecimal("20000.00"), new BigDecimal("25000.00"),
                        SalaryCurrency.PLN, SalaryPeriod.MONTH)),
                "We are building a recruitment platform.",
                List.of("Designing REST APIs"),
                List.of("Java 21"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                null);
    }
}
