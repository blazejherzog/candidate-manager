package com.candidate_manager.job.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface JobOfferRepository extends JpaRepository<JobOffer, UUID> {

    Optional<JobOffer> findByIdAndRecruiterId(UUID id, UUID recruiterId);

    Page<JobOffer> findByRecruiterIdOrderByCreatedAtDescIdDesc(UUID recruiterId, Pageable pageable);
}
