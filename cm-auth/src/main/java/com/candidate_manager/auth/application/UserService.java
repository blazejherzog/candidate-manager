package com.candidate_manager.auth.application;

import com.candidate_manager.auth.domain.UserRole;
import com.candidate_manager.auth.infrastructure.KeycloakAdminClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final KeycloakAdminClient keycloakAdminClient;

    public void registerUser(RegisterUserCommand command, UserRole role) {
        String userId = keycloakAdminClient.createUser(command);
        try {
            keycloakAdminClient.assignRealmRole(userId, role);
        } catch (RuntimeException e) {
            deleteUserLeftWithoutRole(userId);
            throw e;
        }
        log.info("Registered user {} with role {}", userId, role);
    }

    private void deleteUserLeftWithoutRole(String userId) {
        try {
            keycloakAdminClient.deleteUser(userId);
        } catch (RuntimeException e) {
            log.error("User {} was created without a role and could not be deleted; delete it manually in Keycloak", userId, e);
        }
    }
}
