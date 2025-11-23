package com.candidate_manager.auth.web;

import com.candidate_manager.auth.application.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @PostMapping("/register")
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterUserRequest request) {
        userService.registerUser(request);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/hello")
    public ResponseEntity<String> hello() {
        return ResponseEntity.ok("Hello!");
    }
}
