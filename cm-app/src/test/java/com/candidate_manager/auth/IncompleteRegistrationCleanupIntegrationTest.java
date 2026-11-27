package com.candidate_manager.auth;

import com.candidate_manager.KeycloakTestContainer;
import com.candidate_manager.TestcontainersConfiguration;
import com.candidate_manager.auth.application.IncompleteRegistrationCleanup;
import com.candidate_manager.auth.application.RegisterUserCommand;
import com.candidate_manager.auth.application.RegistrationCleanupProperties;
import com.candidate_manager.auth.application.RegistrationExpiredException;
import com.candidate_manager.auth.application.UserAlreadyExistsException;
import com.candidate_manager.auth.application.UserService;
import com.candidate_manager.auth.domain.UserRole;
import com.candidate_manager.auth.infrastructure.KeycloakAdminClient;
import com.candidate_manager.auth.infrastructure.PendingRegistrationRepository;
import com.candidate_manager.shared.config.keycloak.KeycloakProperties;
import com.candidate_manager.shared.config.messaging.publisher.EventPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class IncompleteRegistrationCleanupIntegrationTest {

    private static final Duration READ_TIMEOUT = Duration.ofSeconds(1);
    private static final Duration KEYCLOAK_DELAY = Duration.ofSeconds(3);
    private static final Duration STALE_AFTER = Duration.ofMinutes(2);
    private static final Duration ABANDON_AFTER = Duration.ofHours(1);
    private static final String UNREACHABLE_KEYCLOAK = "http://localhost:1";

    @Autowired
    private KeycloakTestContainer keycloak;

    @Autowired
    private KeycloakProperties keycloakProperties;

    @Autowired
    private EventPublisher eventPublisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PendingRegistrationRepository pendingRegistrationRepository;

    @Autowired
    private UserService userService;

    @Test
    void removesUserWhenKeycloakAnswersUserCreationAfterTimeout() throws Exception {
        String username = uniqueUsername();
        Instant testStart = Instant.now();

        try (SlowKeycloakProxy proxy = new SlowKeycloakProxy(keycloak.url(), KEYCLOAK_DELAY)) {
            proxy.answerUserCreationLate();

            assertThatThrownBy(() -> registrationThrough(proxy.url()).userService().registerUser(command(username), UserRole.CANDIDATE));
            assertThat(proxy.awaitUserCreationStatus()).isEqualTo(201);
        }

        assertThat(keycloak.userExists(username)).isFalse();
        assertThat(countPendingRegistrationsStartedSince(testStart)).isZero();
        userService.registerUser(command(username), UserRole.CANDIDATE);
        assertThat(keycloak.realmRolesOf(username)).contains("CANDIDATE");
    }

    @Test
    void scheduledCleanupRemovesUserCreatedAfterImmediateCleanup() throws Exception {
        String username = uniqueUsername();
        Instant testStart = Instant.now();

        try (SlowKeycloakProxy proxy = new SlowKeycloakProxy(keycloak.url(), KEYCLOAK_DELAY)) {
            proxy.forwardUserCreationLate();

            assertThatThrownBy(() -> registrationThrough(proxy.url()).userService().registerUser(command(username), UserRole.CANDIDATE));
            assertThat(proxy.awaitUserCreationStatus()).isEqualTo(201);
        }

        assertThat(keycloak.userExists(username)).isTrue();
        assertThat(countPendingRegistrationsStartedSince(testStart)).isEqualTo(1);
        cleanupTreatingEveryRegistrationAsStale().removeStaleRegistrations();
        assertThat(keycloak.userExists(username)).isFalse();
        assertThat(countPendingRegistrationsStartedSince(testStart)).isZero();
    }

    @Test
    void keepsExistingUserWhenTimeoutHidesConflict() throws Exception {
        String username = uniqueUsername();
        userService.registerUser(command(username), UserRole.CANDIDATE);
        UUID existingUserId = keycloak.userIdOf(username);

        try (SlowKeycloakProxy proxy = new SlowKeycloakProxy(keycloak.url(), KEYCLOAK_DELAY)) {
            proxy.answerUserCreationLate();

            assertThatThrownBy(() -> registrationThrough(proxy.url()).userService().registerUser(command(username), UserRole.RECRUITER));
            assertThat(proxy.awaitUserCreationStatus()).isEqualTo(409);
        }
        cleanupTreatingEveryRegistrationAsStale().removeStaleRegistrations();

        assertThat(keycloak.userIdOf(username)).isEqualTo(existingUserId);
        assertThat(keycloak.realmRolesOf(username)).contains("CANDIDATE").doesNotContain("RECRUITER");
    }

    @Test
    void forgetsRegistrationRejectedAsDuplicateWithoutTouchingExistingUser() {
        String username = uniqueUsername();
        userService.registerUser(command(username), UserRole.CANDIDATE);
        Instant testStart = Instant.now();

        assertThatThrownBy(() -> userService.registerUser(command(username), UserRole.CANDIDATE))
                .isInstanceOf(UserAlreadyExistsException.class);

        assertThat(countPendingRegistrationsStartedSince(testStart)).isZero();
        assertThat(keycloak.userExists(username)).isTrue();
    }

    @Test
    void removesUserOfRegistrationInterruptedBeforeCompletion() {
        String username = uniqueUsername();
        UUID registrationId = UUID.randomUUID();
        keycloak.createUserWithRegistrationId(username, registrationId);
        pendingRegistrationRepository.insert(registrationId, Instant.now().minus(STALE_AFTER).minusSeconds(1));

        assertThat(cleanup(keycloak.url()).removeStaleRegistrations()).isGreaterThanOrEqualTo(1);

        assertThat(keycloak.userExists(username)).isFalse();
        assertThat(pendingRegistrationExists(registrationId)).isFalse();
    }

    @Test
    void leavesRecentRegistrationToItsRequest() {
        String username = uniqueUsername();
        UUID registrationId = UUID.randomUUID();
        keycloak.createUserWithRegistrationId(username, registrationId);
        pendingRegistrationRepository.insert(registrationId, Instant.now().minus(STALE_AFTER).plusSeconds(30));

        cleanup(keycloak.url()).removeStaleRegistrations();

        assertThat(keycloak.userExists(username)).isTrue();
        assertThat(pendingRegistrationExists(registrationId)).isTrue();
    }

    @Test
    void keepsCheckingStaleRegistrationWithoutUserUntilItIsAbandoned() {
        UUID recentRegistration = UUID.randomUUID();
        UUID abandonedRegistration = UUID.randomUUID();
        pendingRegistrationRepository.insert(recentRegistration, Instant.now().minus(STALE_AFTER).minusSeconds(1));
        pendingRegistrationRepository.insert(abandonedRegistration, Instant.now().minus(ABANDON_AFTER).minusSeconds(1));

        cleanup(keycloak.url()).removeStaleRegistrations();

        assertThat(pendingRegistrationExists(recentRegistration)).isTrue();
        assertThat(pendingRegistrationExists(abandonedRegistration)).isFalse();
    }

    @Test
    void keepsCompletedRegistrationUntouched() {
        String username = uniqueUsername();
        Instant testStart = Instant.now();

        userService.registerUser(command(username), UserRole.RECRUITER);
        cleanupTreatingEveryRegistrationAsStale().removeStaleRegistrations();

        assertThat(countPendingRegistrationsStartedSince(testStart)).isZero();
        assertThat(keycloak.realmRolesOf(username)).contains("RECRUITER");
    }

    @Test
    void skipsRegistrationWhoseCompletionHoldsTheLock() {
        String username = uniqueUsername();
        UUID registrationId = UUID.randomUUID();
        keycloak.createUserWithRegistrationId(username, registrationId);
        pendingRegistrationRepository.insert(registrationId, Instant.now().minus(STALE_AFTER).minusSeconds(1));

        transactionTemplate.executeWithoutResult(completion -> {
            assertThat(pendingRegistrationRepository.delete(registrationId)).isTrue();
            assertThat(CompletableFuture.supplyAsync(() -> cleanup(keycloak.url()).removeStaleRegistrations())
                    .orTimeout(30, TimeUnit.SECONDS).join()).isZero();
        });

        assertThat(keycloak.userExists(username)).isTrue();
        assertThat(pendingRegistrationExists(registrationId)).isFalse();
    }

    @Test
    void failsRegistrationCompletedAfterCleanupRemovedIt() {
        String username = uniqueUsername();
        IncompleteRegistrationCleanup cleanup = cleanupTreatingEveryRegistrationAsStale();
        PendingRegistrationRepository repositoryCleanedUpBeforeCompletion = new PendingRegistrationRepository(jdbcTemplate) {
            @Override
            public boolean delete(UUID registrationId) {
                CompletableFuture.runAsync(cleanup::removeStaleRegistrations).orTimeout(30, TimeUnit.SECONDS).join();
                return super.delete(registrationId);
            }
        };
        KeycloakAdminClient keycloakAdminClient = keycloakAdminClient(keycloak.url());
        UserService registrationRacingCleanup = new UserService(keycloakAdminClient, eventPublisher, transactionTemplate,
                repositoryCleanedUpBeforeCompletion,
                new IncompleteRegistrationCleanup(keycloakAdminClient, pendingRegistrationRepository, transactionTemplate, cleanupProperties(STALE_AFTER)));

        assertThatThrownBy(() -> registrationRacingCleanup.registerUser(command(username), UserRole.CANDIDATE))
                .isInstanceOf(RegistrationExpiredException.class);

        assertThat(keycloak.userExists(username)).isFalse();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM outbox_event WHERE payload->>'email' = ?",
                Integer.class, username + "@example.com")).isZero();
    }

    @Test
    void keepsRegistrationWhenKeycloakIsUnavailable() {
        UUID registrationId = UUID.randomUUID();
        pendingRegistrationRepository.insert(registrationId, Instant.now().minus(ABANDON_AFTER).minusSeconds(1));

        assertThat(cleanup(UNREACHABLE_KEYCLOAK).removeStaleRegistrations()).isZero();

        assertThat(pendingRegistrationExists(registrationId)).isTrue();
        pendingRegistrationRepository.delete(registrationId);
    }

    private Registration registrationThrough(String keycloakUrl) {
        KeycloakAdminClient keycloakAdminClient = keycloakAdminClient(keycloakUrl);
        IncompleteRegistrationCleanup cleanup = new IncompleteRegistrationCleanup(
                keycloakAdminClient, pendingRegistrationRepository, transactionTemplate, cleanupProperties(STALE_AFTER));
        return new Registration(new UserService(
                keycloakAdminClient, eventPublisher, transactionTemplate, pendingRegistrationRepository, cleanup));
    }

    private IncompleteRegistrationCleanup cleanup(String keycloakUrl) {
        return new IncompleteRegistrationCleanup(
                keycloakAdminClient(keycloakUrl), pendingRegistrationRepository, transactionTemplate, cleanupProperties(STALE_AFTER));
    }

    private IncompleteRegistrationCleanup cleanupTreatingEveryRegistrationAsStale() {
        return new IncompleteRegistrationCleanup(
                keycloakAdminClient(keycloak.url()), pendingRegistrationRepository, transactionTemplate, cleanupProperties(Duration.ZERO));
    }

    private KeycloakAdminClient keycloakAdminClient(String keycloakUrl) {
        KeycloakProperties properties = new KeycloakProperties();
        properties.setUrl(keycloakUrl);
        properties.setRealm(keycloakProperties.getRealm());
        properties.setClientId(keycloakProperties.getClientId());
        properties.setClientSecret(keycloakProperties.getClientSecret());
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
        requestFactory.setReadTimeout(READ_TIMEOUT);
        return new KeycloakAdminClient(properties, RestClient.builder().requestFactory(requestFactory));
    }

    private int countPendingRegistrationsStartedSince(Instant testStart) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pending_registration WHERE created_at >= ?",
                Integer.class, Timestamp.from(testStart));
    }

    private boolean pendingRegistrationExists(UUID registrationId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pending_registration WHERE id = ?", Integer.class, registrationId) == 1;
    }

    private static RegistrationCleanupProperties cleanupProperties(Duration staleAfter) {
        return new RegistrationCleanupProperties(staleAfter, ABANDON_AFTER, 20);
    }

    private static RegisterUserCommand command(String username) {
        return new RegisterUserCommand(username, username + "@example.com", "Secret-pass-1", "Jan", "Kowalski");
    }

    private static String uniqueUsername() {
        return "user" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private record Registration(UserService userService) {
    }
}
