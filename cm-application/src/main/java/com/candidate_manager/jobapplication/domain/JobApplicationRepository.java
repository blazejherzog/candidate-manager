package com.candidate_manager.jobapplication.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface JobApplicationRepository extends JpaRepository<JobApplication, UUID> {

    Optional<JobApplication> findByIdAndCandidateId(UUID id, UUID candidateId);

    Optional<JobApplication> findByIdAndRecruiterId(UUID id, UUID recruiterId);

    boolean existsByIdAndCandidateId(UUID id, UUID candidateId);

    boolean existsByIdAndRecruiterId(UUID id, UUID recruiterId);

    Page<JobApplication> findByJobOfferIdAndStatusInOrderBySubmittedAtDescIdDesc(
            UUID jobOfferId, Collection<JobApplicationStatus> statuses, Pageable pageable);

    Page<JobApplication> findByCandidateIdOrderBySubmittedAtDescIdDesc(UUID candidateId, Pageable pageable);
}
