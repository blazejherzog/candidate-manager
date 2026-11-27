package com.candidate_manager.auth.application;

import com.candidate_manager.auth.domain.PendingRegistration;
import com.candidate_manager.auth.infrastructure.KeycloakAdminClient;
import com.candidate_manager.auth.infrastructure.PendingRegistrationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class IncompleteRegistrationCleanup {

    private final KeycloakAdminClient keycloakAdminClient;
    private final PendingRegistrationRepository pendingRegistrationRepository;
    private final TransactionTemplate transactionTemplate;
    private final RegistrationCleanupProperties properties;

    public void cleanUpAfterFailure(UUID registrationId) {
        try {
            transactionTemplate.executeWithoutResult(transaction -> {
                if (pendingRegistrationRepository.lock(registrationId) && deleteUserOf(registrationId)) {
                    pendingRegistrationRepository.delete(registrationId);
                }
            });
        } catch (RuntimeException cleanupFailure) {
            log.warn("Could not clean up failed registration {} now; the scheduled cleanup retries it", registrationId, cleanupFailure);
        }
    }

    public int removeStaleRegistrations() {
        Instant now = Instant.now();
        return transactionTemplate.execute(transaction -> {
            List<PendingRegistration> staleRegistrations = pendingRegistrationRepository.lockStartedBefore(
                    now.minus(properties.staleAfter()), properties.batchSize());
            int resolvedRegistrations = 0;
            for (PendingRegistration registration : staleRegistrations) {
                if (resolve(registration, now)) {
                    pendingRegistrationRepository.delete(registration.id());
                    resolvedRegistrations++;
                }
            }
            return resolvedRegistrations;
        });
    }

    private boolean resolve(PendingRegistration registration, Instant now) {
        try {
            return deleteUserOf(registration.id()) || registration.createdAt().isBefore(now.minus(properties.abandonAfter()));
        } catch (IdentityProviderException keycloakUnavailable) {
            log.warn("Could not check incomplete registration {}; retrying on the next run", registration.id(), keycloakUnavailable);
            return false;
        }
    }

    private boolean deleteUserOf(UUID registrationId) {
        Optional<String> userId = keycloakAdminClient.findUserIdByRegistrationId(registrationId);
        userId.ifPresent(id -> {
            keycloakAdminClient.deleteUserIfExists(id);
            log.warn("Deleted user {} created by incomplete registration {}", id, registrationId);
        });
        return userId.isPresent();
    }
}
