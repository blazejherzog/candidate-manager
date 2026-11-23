package com.candidate_manager.candidate.application;

import com.candidate_manager.candidate.domain.Candidate;
import com.candidate_manager.candidate.domain.CandidateDetails;
import com.candidate_manager.candidate.domain.CandidateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CandidateProfileService {

    private final CandidateRepository candidateRepository;

    @Transactional
    public void createProfile(UUID userId, String email, String firstName, String lastName) {
        Candidate candidate = candidateRepository.save(new Candidate(userId, email, firstName, lastName));
        log.info("Created candidate profile {} for user {}", candidate.getId(), userId);
    }

    @Transactional(readOnly = true)
    public CandidateProfile findProfile(UUID userId) {
        return CandidateProfile.from(findCandidate(userId));
    }

    @Transactional
    public CandidateProfile updateProfile(UUID userId, CandidateDetails details, long expectedVersion) {
        Candidate candidate = findCandidate(userId);
        if (!candidate.hasVersion(expectedVersion)) {
            throw new ObjectOptimisticLockingFailureException(Candidate.class, candidate.getId());
        }
        candidate.updateDetails(details, Instant.now());
        CandidateProfile updatedProfile = CandidateProfile.from(candidateRepository.saveAndFlush(candidate));
        log.info("Updated candidate profile {} to version {}", updatedProfile.id(), updatedProfile.version());
        return updatedProfile;
    }

    private Candidate findCandidate(UUID userId) {
        return candidateRepository.findByUserId(userId).orElseThrow(CandidateProfileNotFoundException::new);
    }
}
