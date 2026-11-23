package com.candidate_manager.job.application;

import com.candidate_manager.job.domain.JobOffer;
import com.candidate_manager.job.domain.JobOfferRepository;
import com.candidate_manager.shared.directory.ActiveJobOffer;
import com.candidate_manager.shared.directory.JobOfferDirectory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class JobOfferDirectoryService implements JobOfferDirectory {

    private final JobOfferRepository jobOfferRepository;
    private final Clock businessClock;

    @Override
    @Transactional(readOnly = true)
    public Optional<ActiveJobOffer> findActiveOffer(UUID jobOfferId) {
        LocalDate today = LocalDate.now(businessClock);
        return jobOfferRepository.findById(jobOfferId)
                .filter(offer -> offer.isActive(today))
                .map(JobOfferDirectoryService::toActiveJobOffer);
    }

    private static ActiveJobOffer toActiveJobOffer(JobOffer offer) {
        return new ActiveJobOffer(offer.getId(), offer.getRecruiterId(), offer.getTitle(), offer.getCompanyName());
    }
}
