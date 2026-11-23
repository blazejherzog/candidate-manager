package com.candidate_manager.candidate.application;

import com.candidate_manager.candidate.domain.Candidate;
import com.candidate_manager.candidate.domain.CandidateRepository;
import com.candidate_manager.shared.directory.CandidateDirectory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class CandidateDirectoryService implements CandidateDirectory {

    private final CandidateRepository candidateRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> findCandidateIdOfUser(UUID userId) {
        return candidateRepository.findByUserId(userId).map(Candidate::getId);
    }
}
