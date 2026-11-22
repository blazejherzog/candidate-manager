package com.candidate_manager.job.application;

import com.candidate_manager.job.domain.JobOffer;
import com.candidate_manager.job.domain.JobOfferContent;
import com.candidate_manager.job.domain.JobOfferRepository;
import com.candidate_manager.job.domain.PublishedJobOfferCriteria;
import com.candidate_manager.job.domain.PublishedJobOfferSpecifications;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobOfferService {

    private static final int PUBLISHED_OFFER_SEARCH_TIMEOUT_SECONDS = 3;
    private static final Sort NEWEST_PUBLISHED_FIRST = Sort.by(Sort.Order.desc("publishedAt"), Sort.Order.desc("id"));

    private final JobOfferRepository jobOfferRepository;
    private final Clock businessClock;

    @Transactional
    public JobOfferDetails createDraft(UUID recruiterId, JobOfferContent content) {
        JobOffer draft = jobOfferRepository.save(JobOffer.draft(recruiterId, content, Instant.now()));
        log.info("Recruiter {} created job offer draft {}", recruiterId, draft.getId());
        return JobOfferDetails.from(draft, today());
    }

    @Transactional
    public JobOfferDetails updateDraft(UUID jobOfferId, UUID recruiterId, JobOfferContent content) {
        JobOffer offer = findOwnedOffer(jobOfferId, recruiterId);
        offer.updateDraft(content, Instant.now());
        return JobOfferDetails.from(offer, today());
    }

    @Transactional
    public void deleteDraft(UUID jobOfferId, UUID recruiterId) {
        JobOffer offer = findOwnedOffer(jobOfferId, recruiterId);
        offer.requireDraft();
        jobOfferRepository.delete(offer);
        log.info("Recruiter {} deleted job offer draft {}", recruiterId, jobOfferId);
    }

    @Transactional
    public JobOfferDetails publish(UUID jobOfferId, UUID recruiterId) {
        JobOffer offer = findOwnedOffer(jobOfferId, recruiterId);
        LocalDate today = today();
        offer.publish(today, Instant.now());
        log.info("Recruiter {} published job offer {} valid until {}", recruiterId, jobOfferId, offer.getValidUntil());
        return JobOfferDetails.from(offer, today);
    }

    @Transactional(readOnly = true)
    public JobOfferDetails findVisibleOffer(UUID jobOfferId, UUID viewerIdOrNullIfAnonymous) {
        LocalDate today = today();
        return jobOfferRepository.findById(jobOfferId)
                .filter(offer -> offer.isVisibleTo(viewerIdOrNullIfAnonymous, today))
                .map(offer -> JobOfferDetails.from(offer, today))
                .orElseThrow(JobOfferNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public JobOfferSummaryPage findRecruiterOffers(UUID recruiterId, int page, int size) {
        return JobOfferSummaryPage.from(
                jobOfferRepository.findByRecruiterIdOrderByCreatedAtDescIdDesc(recruiterId, PageRequest.of(page, size)),
                today());
    }

    @Transactional(readOnly = true, timeout = PUBLISHED_OFFER_SEARCH_TIMEOUT_SECONDS)
    public PublishedJobOfferPage findActiveOffers(PublishedJobOfferCriteria criteria, int page, int size) {
        return PublishedJobOfferPage.from(jobOfferRepository.findAll(
                PublishedJobOfferSpecifications.activeOffersMatching(criteria, today()),
                PageRequest.of(page, size, NEWEST_PUBLISHED_FIRST)));
    }

    private LocalDate today() {
        return LocalDate.now(businessClock);
    }

    private JobOffer findOwnedOffer(UUID jobOfferId, UUID recruiterId) {
        return jobOfferRepository.findByIdAndRecruiterId(jobOfferId, recruiterId)
                .orElseThrow(JobOfferNotFoundException::new);
    }
}
