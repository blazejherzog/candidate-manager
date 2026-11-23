package com.candidate_manager.candidate.application;

import com.candidate_manager.candidate.domain.Candidate;
import com.candidate_manager.candidate.domain.CandidateRepository;
import com.candidate_manager.shared.directory.CandidateContact;
import com.candidate_manager.shared.directory.CandidateDirectory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
class CandidateDirectoryService implements CandidateDirectory {

    private final CandidateRepository candidateRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> findCandidateIdOfUser(UUID userId) {
        return candidateRepository.findByUserId(userId).map(Candidate::getId);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, CandidateContact> findContactsByCandidateId(Collection<UUID> candidateIds) {
        return candidateRepository.findAllById(candidateIds).stream()
                .map(CandidateDirectoryService::toContact)
                .collect(Collectors.toMap(CandidateContact::candidateId, Function.identity()));
    }

    private static CandidateContact toContact(Candidate candidate) {
        return new CandidateContact(
                candidate.getId(),
                candidate.getFirstName(),
                candidate.getLastName(),
                candidate.getEmail(),
                candidate.getPhoneNumber());
    }
}
