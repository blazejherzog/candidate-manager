package com.candidate_manager.auth.application;

import com.candidate_manager.auth.domain.UserRole;
import com.candidate_manager.auth.infrastructure.KeycloakAdminClient;
import com.candidate_manager.auth.infrastructure.PendingRegistrationRepository;
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
    private final PendingRegistrationRepository pendingRegistrationRepository;
    private final IncompleteRegistrationCleanup incompleteRegistrationCleanup;

    public void registerUser(RegisterUserCommand command, UserRole role) {
        UUID registrationId = UUID.randomUUID();
        pendingRegistrationRepository.insert(registrationId, Instant.now());
        String userId;
        try {
            userId = keycloakAdminClient.createUser(command, registrationId);
            keycloakAdminClient.assignRealmRole(userId, role);
            transactionTemplate.executeWithoutResult(transaction -> completeRegistration(registrationId, userId, command, role));
        } catch (UserAlreadyExistsException nothingWasCreated) {
            pendingRegistrationRepository.delete(registrationId);
            throw nothingWasCreated;
        } catch (RuntimeException registrationFailure) {
            incompleteRegistrationCleanup.cleanUpAfterFailure(registrationId);
            throw registrationFailure;
        }
        log.info("Registered user {} with role {}", userId, role);
    }

    private void completeRegistration(UUID registrationId, String userId, RegisterUserCommand command, UserRole role) {
        if (role == UserRole.CANDIDATE) {
            publishCandidateRegistered(userId, command);
        }
        if (!pendingRegistrationRepository.delete(registrationId)) {
            throw new RegistrationExpiredException();
        }
    }

    private void publishCandidateRegistered(String userId, RegisterUserCommand command) {
        CandidateRegisteredEvent event = new CandidateRegisteredEvent(
                UUID.randomUUID(),
                Instant.now(),
                UUID.fromString(userId),
                command.email(),
                command.firstName(),
                command.lastName());
        eventPublisher.publish(event);
    }
}
