package com.candidate_manager.jobapplication.application;

import com.candidate_manager.jobapplication.domain.JobApplicationRepository;
import com.candidate_manager.shared.directory.CandidateDirectory;
import com.candidate_manager.shared.directory.JobApplicationDirectory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
class JobApplicationDirectoryService implements JobApplicationDirectory {

    private final JobApplicationRepository jobApplicationRepository;
    private final CandidateDirectory candidateDirectory;

    @Override
    @Transactional(readOnly = true)
    public boolean isVisibleToCandidate(UUID jobApplicationId, UUID userId) {
        return candidateDirectory.findCandidateIdOfUser(userId)
                .map(candidateId -> jobApplicationRepository.existsByIdAndCandidateId(jobApplicationId, candidateId))
                .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isVisibleToRecruiter(UUID jobApplicationId, UUID recruiterId) {
        return jobApplicationRepository.existsByIdAndRecruiterId(jobApplicationId, recruiterId);
    }
}
