package com.candidate_manager.auth.application;

import com.candidate_manager.auth.domain.UserRole;
import com.candidate_manager.auth.infrastructure.KeycloakAdminClient;
import com.candidate_manager.shared.config.messaging.publisher.EventPublisher;
import com.candidate_manager.shared.event.CandidateRegisteredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final KeycloakAdminClient keycloakAdminClient;
    private final EventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;

    public void registerUser(RegisterUserCommand command, UserRole role) {
        String userId = keycloakAdminClient.createUser(command);
        try {
            keycloakAdminClient.assignRealmRole(userId, role);
            if (role == UserRole.CANDIDATE) {
                publishCandidateRegistered(userId, command);
            }
        } catch (RuntimeException e) {
            deleteIncompletelyRegisteredUser(userId);
            throw e;
        }
        log.info("Registered user {} with role {}", userId, role);
    }

    private void publishCandidateRegistered(String userId, RegisterUserCommand command) {
        CandidateRegisteredEvent event = new CandidateRegisteredEvent(
                UUID.randomUUID(),
                Instant.now(),
                UUID.fromString(userId),
                command.email(),
                command.firstName(),
                command.lastName());
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(event));
    }

    private void deleteIncompletelyRegisteredUser(String userId) {
        try {
            keycloakAdminClient.deleteUser(userId);
        } catch (RuntimeException e) {
            log.error("User {} was not fully registered and could not be deleted; delete it manually in Keycloak", userId, e);
        }
    }
}
