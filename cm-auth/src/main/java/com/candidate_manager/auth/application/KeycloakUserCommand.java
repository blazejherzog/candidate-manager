package com.candidate_manager.auth.application;

import com.candidate_manager.auth.web.RegisterUserRequest;

import java.util.List;

public record KeycloakUserCommand(
        String username,
        String email,
        boolean enabled,
        List<Credential> credentials
) {

    public record Credential(
            String type,
            String value,
            boolean temporary
    ) {}

    public static KeycloakUserCommand from(RegisterUserRequest request) {
        return new KeycloakUserCommand(
                request.getUsername(),
                request.getEmail(),
                true,
                List.of(new Credential("password", request.getPassword(), false))
        );
    }
}
