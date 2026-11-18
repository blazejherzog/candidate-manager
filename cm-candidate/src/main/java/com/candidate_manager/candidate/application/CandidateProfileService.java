package com.candidate_manager.candidate.application;

import com.candidate_manager.candidate.domain.Candidate;
import com.candidate_manager.candidate.domain.CandidateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}
