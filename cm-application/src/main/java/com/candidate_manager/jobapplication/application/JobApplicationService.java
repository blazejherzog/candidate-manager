package com.candidate_manager.jobapplication.application;

import com.candidate_manager.jobapplication.domain.JobApplication;
import com.candidate_manager.jobapplication.domain.JobApplicationRepository;
import com.candidate_manager.shared.directory.ActiveJobOffer;
import com.candidate_manager.shared.directory.CandidateDirectory;
import com.candidate_manager.shared.directory.JobOfferDirectory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobApplicationService {

    private final JobApplicationRepository jobApplicationRepository;
    private final JobOfferDirectory jobOfferDirectory;
    private final CandidateDirectory candidateDirectory;

    @Transactional
    public JobApplicationDetails apply(UUID jobOfferId, UUID userId, String message) {
        ActiveJobOffer offer = jobOfferDirectory.findActiveOffer(jobOfferId)
                .orElseThrow(ActiveJobOfferNotFoundException::new);
        UUID candidateId = candidateDirectory.findCandidateIdOfUser(userId)
                .orElseThrow(CandidateProfileMissingException::new);
        JobApplication application = saveUnlessAlreadyApplied(
                JobApplication.submit(offer, candidateId, message, Instant.now()));
        log.info("Candidate {} applied for job offer {} with application {}", candidateId, jobOfferId, application.getId());
        return JobApplicationDetails.from(application);
    }

    @Transactional(readOnly = true)
    public JobApplicationSummaryPage findOwnApplications(UUID userId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size);
        Page<JobApplication> applications = candidateDirectory.findCandidateIdOfUser(userId)
                .map(candidateId -> jobApplicationRepository.findByCandidateIdOrderBySubmittedAtDescIdDesc(candidateId, pageRequest))
                .orElseGet(() -> Page.empty(pageRequest));
        return JobApplicationSummaryPage.from(applications);
    }

    @Transactional(readOnly = true)
    public JobApplicationDetails findOwnApplication(UUID jobApplicationId, UUID userId) {
        return JobApplicationDetails.from(findApplicationOfUser(jobApplicationId, userId));
    }

    @Transactional
    public JobApplicationDetails withdraw(UUID jobApplicationId, UUID userId) {
        JobApplication application = findApplicationOfUser(jobApplicationId, userId);
        application.withdraw(Instant.now());
        log.info("Candidate {} withdrew application {}", application.getCandidateId(), jobApplicationId);
        return JobApplicationDetails.from(application);
    }

    private JobApplication findApplicationOfUser(UUID jobApplicationId, UUID userId) {
        return candidateDirectory.findCandidateIdOfUser(userId)
                .flatMap(candidateId -> jobApplicationRepository.findByIdAndCandidateId(jobApplicationId, candidateId))
                .orElseThrow(JobApplicationNotFoundException::new);
    }

    private JobApplication saveUnlessAlreadyApplied(JobApplication application) {
        try {
            return jobApplicationRepository.saveAndFlush(application);
        } catch (DataIntegrityViolationException violation) {
            if (violatesOneApplicationPerCandidateAndOffer(violation)) {
                throw new AlreadyAppliedException();
            }
            throw violation;
        }
    }

    private static boolean violatesOneApplicationPerCandidateAndOffer(DataIntegrityViolationException violation) {
        return violation.getCause() instanceof ConstraintViolationException constraintViolation
                && JobApplication.ONE_PER_CANDIDATE_AND_OFFER_CONSTRAINT.equals(constraintViolation.getConstraintName());
    }
}
