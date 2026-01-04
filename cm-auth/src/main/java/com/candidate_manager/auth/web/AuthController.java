package com.candidate_manager.auth.web;

import com.candidate_manager.auth.application.UserService;
import com.candidate_manager.shared.config.messaging.event.CandidateCreatedEvent;
import com.candidate_manager.shared.config.messaging.publisher.EventPublisher;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final EventPublisher eventPublisher;

    @PostMapping("/register")
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterUserRequest request) {
        userService.registerUser(request);
        return ResponseEntity.ok().build();
    }

    //TODO: Only for testing, to be removed
    @GetMapping("/helloRabbit")
    public ResponseEntity<String> hello() {
        CandidateCreatedEvent event = new CandidateCreatedEvent(
                UUID.randomUUID(),
                "12345",
                "Alojzy",
                "Brzeczyszczykiewicz",
                "test@gmail.com"
        );
        eventPublisher.publish(event);
        return ResponseEntity.ok("Hello!");
    }
}
