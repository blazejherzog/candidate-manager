package com.candidate_manager.candidate.web;

import com.candidate_manager.candidate.application.CandidateProfile;
import com.candidate_manager.candidate.application.CandidateProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/candidates/me")
@PreAuthorize("hasRole('CANDIDATE')")
@RequiredArgsConstructor
public class CandidateProfileController {

    private final CandidateProfileService candidateProfileService;

    @GetMapping
    public CandidateProfile findOwnProfile(Principal candidate) {
        return candidateProfileService.findProfile(userIdOf(candidate));
    }

    @PutMapping
    public CandidateProfile updateOwnProfile(@Valid @RequestBody UpdateCandidateProfileRequest request,
                                             Principal candidate) {
        return candidateProfileService.updateProfile(userIdOf(candidate), request.toDetails(), request.version());
    }

    private static UUID userIdOf(Principal principal) {
        return UUID.fromString(principal.getName());
    }
}
