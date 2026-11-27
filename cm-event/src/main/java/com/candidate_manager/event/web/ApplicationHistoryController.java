package com.candidate_manager.event.web;

import com.candidate_manager.event.application.ApplicationHistoryEntryView;
import com.candidate_manager.event.application.ApplicationHistoryService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ApplicationHistoryController {

    private final ApplicationHistoryService applicationHistoryService;

    @GetMapping("/api/applications/{id}/history")
    @PreAuthorize("hasAnyRole('CANDIDATE', 'RECRUITER')")
    public List<ApplicationHistoryEntryView> findHistory(@PathVariable("id") UUID jobApplicationId, Principal user,
                                                         HttpServletRequest request) {
        UUID userId = UUID.fromString(user.getName());
        return request.isUserInRole("RECRUITER")
                ? applicationHistoryService.findHistoryForRecruiter(jobApplicationId, userId)
                : applicationHistoryService.findHistoryForCandidate(jobApplicationId, userId);
    }
}
