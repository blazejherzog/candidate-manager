package com.candidate_manager.auth.web;

import com.candidate_manager.auth.application.UserService;
import com.candidate_manager.auth.domain.UserRole;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/register")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @PostMapping("/candidate")
    @ResponseStatus(HttpStatus.CREATED)
    public void registerCandidate(@Valid @RequestBody RegisterUserRequest request) {
        userService.registerUser(request.toCommand(), UserRole.CANDIDATE);
    }

    @PostMapping("/recruiter")
    @ResponseStatus(HttpStatus.CREATED)
    public void registerRecruiter(@Valid @RequestBody RegisterUserRequest request) {
        userService.registerUser(request.toCommand(), UserRole.RECRUITER);
    }
}
