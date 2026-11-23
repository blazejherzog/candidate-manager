package com.candidate_manager.jobapplication.application;

import com.candidate_manager.jobapplication.domain.JobApplication;
import com.candidate_manager.shared.directory.CandidateContact;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ReceivedJobApplicationPage(
        List<ReceivedJobApplication> applications,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    static ReceivedJobApplicationPage from(Page<JobApplication> applications, Map<UUID, CandidateContact> contactsByCandidateId) {
        return new ReceivedJobApplicationPage(
                applications.map(application -> ReceivedJobApplication.from(
                        application, contactsByCandidateId.get(application.getCandidateId()))).getContent(),
                applications.getNumber(),
                applications.getSize(),
                applications.getTotalElements(),
                applications.getTotalPages());
    }
}
