package com.candidate_manager.jobapplication.web;

import com.candidate_manager.jobapplication.application.JobApplicationDetails;
import com.candidate_manager.jobapplication.application.JobApplicationService;
import com.candidate_manager.jobapplication.application.JobApplicationSummaryPage;
import com.candidate_manager.jobapplication.application.ReceivedJobApplicationPage;
import com.candidate_manager.jobapplication.domain.JobApplicationStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.security.Principal;
import java.util.Set;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class JobApplicationController {

    private static final int MAX_PAGE_NUMBER = 10_000;

    private final JobApplicationService jobApplicationService;

    @PostMapping("/api/job-offers/{jobOfferId}/applications")
    @PreAuthorize("hasRole('CANDIDATE')")
    public ResponseEntity<JobApplicationDetails> apply(@PathVariable("jobOfferId") UUID jobOfferId,
                                                       @Valid @RequestBody ApplyForJobOfferRequest request,
                                                       Principal candidate) {
        JobApplicationDetails application = jobApplicationService.apply(jobOfferId, userIdOf(candidate), request.message());
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/applications/{id}")
                .buildAndExpand(application.id())
                .toUri();
        return ResponseEntity.created(location).body(application);
    }

    @GetMapping("/api/candidates/me/applications")
    @PreAuthorize("hasRole('CANDIDATE')")
    public JobApplicationSummaryPage findOwnApplications(
            @RequestParam(name = "page", defaultValue = "0") @Min(0) @Max(MAX_PAGE_NUMBER) int page,
            @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size,
            Principal candidate) {
        return jobApplicationService.findOwnApplications(userIdOf(candidate), page, size);
    }

    @GetMapping("/api/applications/{id}")
    @PreAuthorize("hasAnyRole('CANDIDATE', 'RECRUITER')")
    public JobApplicationDetails findApplication(@PathVariable("id") UUID id, Principal user, HttpServletRequest request) {
        return request.isUserInRole("RECRUITER")
                ? jobApplicationService.findReceivedApplication(id, userIdOf(user))
                : jobApplicationService.findOwnApplication(id, userIdOf(user));
    }

    @PostMapping("/api/applications/{id}/withdraw")
    @PreAuthorize("hasRole('CANDIDATE')")
    public JobApplicationDetails withdraw(@PathVariable("id") UUID id, Principal candidate) {
        return jobApplicationService.withdraw(id, userIdOf(candidate));
    }

    @GetMapping("/api/job-offers/{jobOfferId}/applications")
    @PreAuthorize("hasRole('RECRUITER')")
    public ReceivedJobApplicationPage findReceivedApplications(
            @PathVariable("jobOfferId") UUID jobOfferId,
            @RequestParam(name = "status", defaultValue = "") Set<JobApplicationStatus> statuses,
            @RequestParam(name = "page", defaultValue = "0") @Min(0) @Max(MAX_PAGE_NUMBER) int page,
            @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size,
            Principal recruiter) {
        return jobApplicationService.findReceivedApplications(jobOfferId, userIdOf(recruiter), statuses, page, size);
    }

    @PostMapping("/api/applications/{id}/status")
    @PreAuthorize("hasRole('RECRUITER')")
    public JobApplicationDetails changeStatus(@PathVariable("id") UUID id,
                                              @Valid @RequestBody ChangeJobApplicationStatusRequest request,
                                              Principal recruiter) {
        return jobApplicationService.changeStatus(id, userIdOf(recruiter), request.status(), request.version());
    }

    private static UUID userIdOf(Principal principal) {
        return UUID.fromString(principal.getName());
    }
}
