package com.candidate_manager.auth.application;

import com.candidate_manager.auth.infrastructure.KeycloakAdminClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.candidate_manager.auth.web.RegisterUserRequest;

@Service
@RequiredArgsConstructor
public class UserService {

    private final KeycloakAdminClient keycloakAdminClient;

    public void registerUser(RegisterUserRequest request) {
        KeycloakUserCommand keycloakUserCommand = KeycloakUserCommand.from(request);
        keycloakAdminClient.createUserWithRole(keycloakUserCommand, request.getRole());
    }
}
