package com.candidate_manager.job.web;

import com.candidate_manager.job.application.JobOfferDetails;
import com.candidate_manager.job.application.JobOfferService;
import com.candidate_manager.job.application.JobOfferSummaryPage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.security.Principal;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class JobOfferController {

    private final JobOfferService jobOfferService;

    @PostMapping("/api/job-offers")
    @PreAuthorize("hasRole('RECRUITER')")
    public ResponseEntity<JobOfferDetails> createDraft(@Valid @RequestBody JobOfferRequest request, Principal recruiter) {
        JobOfferDetails draft = jobOfferService.createDraft(userIdOf(recruiter), request.toContent());
        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{id}")
                .buildAndExpand(draft.id())
                .toUri();
        return ResponseEntity.created(location).body(draft);
    }

    @PutMapping("/api/job-offers/{id}")
    @PreAuthorize("hasRole('RECRUITER')")
    public JobOfferDetails updateDraft(@PathVariable("id") UUID id, @Valid @RequestBody JobOfferRequest request,
                                       Principal recruiter) {
        return jobOfferService.updateDraft(id, userIdOf(recruiter), request.toContent());
    }

    @DeleteMapping("/api/job-offers/{id}")
    @PreAuthorize("hasRole('RECRUITER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDraft(@PathVariable("id") UUID id, Principal recruiter) {
        jobOfferService.deleteDraft(id, userIdOf(recruiter));
    }

    @PostMapping("/api/job-offers/{id}/publish")
    @PreAuthorize("hasRole('RECRUITER')")
    public JobOfferDetails publish(@PathVariable("id") UUID id, Principal recruiter) {
        return jobOfferService.publish(id, userIdOf(recruiter));
    }

    @GetMapping("/api/job-offers/{id}")
    public JobOfferDetails findOffer(@PathVariable("id") UUID id, Principal user) {
        return jobOfferService.findVisibleOffer(id, userIdOf(user));
    }

    @GetMapping("/api/recruiter/job-offers")
    @PreAuthorize("hasRole('RECRUITER')")
    public JobOfferSummaryPage findRecruiterOffers(
            @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size,
            Principal recruiter) {
        return jobOfferService.findRecruiterOffers(userIdOf(recruiter), page, size);
    }

    private static UUID userIdOf(Principal principal) {
        return UUID.fromString(principal.getName());
    }
}
