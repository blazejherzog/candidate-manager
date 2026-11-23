package com.candidate_manager.jobapplication.application;

import com.candidate_manager.jobapplication.domain.JobApplication;
import com.candidate_manager.jobapplication.domain.JobApplicationRepository;
import com.candidate_manager.jobapplication.domain.JobApplicationStatus;
import com.candidate_manager.shared.directory.ActiveJobOffer;
import com.candidate_manager.shared.directory.CandidateContact;
import com.candidate_manager.shared.directory.CandidateDirectory;
import com.candidate_manager.shared.directory.JobOfferDirectory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
                .orElseThrow(JobOfferNotFoundException::new);
        UUID candidateId = candidateDirectory.findCandidateIdOfUser(userId)
                .orElseThrow(CandidateProfileMissingException::new);
        JobApplication application = saveUnlessAlreadyApplied(
                JobApplication.submit(offer, candidateId, message, Instant.now()));
        log.info("Candidate {} applied for job offer {} with application {}", candidateId, jobOfferId, application.getId());
        return JobApplicationDetails.forCandidate(application);
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
        return JobApplicationDetails.forCandidate(findApplicationOfUser(jobApplicationId, userId));
    }

    @Transactional
    public JobApplicationDetails withdraw(UUID jobApplicationId, UUID userId) {
        JobApplication application = findApplicationOfUser(jobApplicationId, userId);
        application.withdraw(Instant.now());
        jobApplicationRepository.saveAndFlush(application);
        log.info("Candidate {} withdrew application {}", application.getCandidateId(), jobApplicationId);
        return JobApplicationDetails.forCandidate(application);
    }

    @Transactional(readOnly = true)
    public ReceivedJobApplicationPage findReceivedApplications(UUID jobOfferId, UUID recruiterId,
                                                               Set<JobApplicationStatus> statuses, int page, int size) {
        if (!jobOfferDirectory.isOfferOwnedBy(jobOfferId, recruiterId)) {
            throw new JobOfferNotFoundException();
        }
        Page<JobApplication> applications = jobApplicationRepository.findByJobOfferIdAndStatusInOrderBySubmittedAtDescIdDesc(
                jobOfferId, allStatusesIfEmpty(statuses), PageRequest.of(page, size));
        List<UUID> candidateIds = applications.map(JobApplication::getCandidateId).getContent();
        return ReceivedJobApplicationPage.from(applications, candidateDirectory.findContactsByCandidateId(candidateIds));
    }

    @Transactional(readOnly = true)
    public JobApplicationDetails findReceivedApplication(UUID jobApplicationId, UUID recruiterId) {
        return detailsForRecruiter(findApplicationReceivedBy(jobApplicationId, recruiterId));
    }

    @Transactional
    public JobApplicationDetails changeStatus(UUID jobApplicationId, UUID recruiterId, JobApplicationStatus newStatus,
                                              long expectedVersion) {
        JobApplication application = findApplicationReceivedBy(jobApplicationId, recruiterId);
        if (!application.hasVersion(expectedVersion)) {
            throw new ObjectOptimisticLockingFailureException(JobApplication.class, jobApplicationId);
        }
        application.changeStatus(newStatus, Instant.now());
        jobApplicationRepository.saveAndFlush(application);
        log.info("Recruiter {} changed application {} to {}", recruiterId, jobApplicationId, newStatus);
        return detailsForRecruiter(application);
    }

    private JobApplicationDetails detailsForRecruiter(JobApplication application) {
        Map<UUID, CandidateContact> contacts = candidateDirectory.findContactsByCandidateId(List.of(application.getCandidateId()));
        return JobApplicationDetails.forRecruiter(application, contacts.get(application.getCandidateId()));
    }

    private JobApplication findApplicationReceivedBy(UUID jobApplicationId, UUID recruiterId) {
        return jobApplicationRepository.findByIdAndRecruiterId(jobApplicationId, recruiterId)
                .orElseThrow(JobApplicationNotFoundException::new);
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

    private static Set<JobApplicationStatus> allStatusesIfEmpty(Set<JobApplicationStatus> statuses) {
        return statuses.isEmpty() ? EnumSet.allOf(JobApplicationStatus.class) : statuses;
    }

    private static boolean violatesOneApplicationPerCandidateAndOffer(DataIntegrityViolationException violation) {
        return violation.getCause() instanceof ConstraintViolationException constraintViolation
                && JobApplication.ONE_PER_CANDIDATE_AND_OFFER_CONSTRAINT.equals(constraintViolation.getConstraintName());
    }
}
