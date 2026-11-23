package com.candidate_manager.candidate;

import com.candidate_manager.TestcontainersConfiguration;
import com.candidate_manager.candidate.application.CandidateProfileService;
import com.candidate_manager.candidate.domain.CandidateDetails;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class CandidateProfileConcurrencyIntegrationTest {

    private static final int CONCURRENT_UPDATES = 4;
    private static final String SESSIONS_WAITING_FOR_LOCK = """
            SELECT COUNT(*) FROM pg_stat_activity
            WHERE datname = current_database() AND wait_event_type = 'Lock' AND query LIKE 'update candidate %'
            """;

    @Autowired
    private CandidateProfileService candidateProfileService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void acceptsExactlyOneOfConcurrentUpdatesBasedOnSameVersion() throws Exception {
        UUID userId = UUID.randomUUID();
        candidateProfileService.createProfile(userId, userId + "@example.com", "Jan", "Kowalski");
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Optional<String>>> updates = new ArrayList<>();

        try (ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_UPDATES)) {
            transactionTemplate.executeWithoutResult(transactionHoldingRowLock -> {
                jdbcTemplate.queryForMap("SELECT id FROM candidate WHERE user_id = ? FOR UPDATE", userId);
                for (int updateNumber = 0; updateNumber < CONCURRENT_UPDATES; updateNumber++) {
                    String firstName = "Anna" + updateNumber;
                    updates.add(executor.submit(() -> {
                        start.await();
                        return savedFirstNameUnlessConflict(userId, firstName);
                    }));
                }
                start.countDown();
                await().atMost(Duration.ofSeconds(10)).until(() ->
                        jdbcTemplate.queryForObject(SESSIONS_WAITING_FOR_LOCK, Integer.class) == CONCURRENT_UPDATES);
            });
            executor.shutdown();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }

        List<String> savedFirstNames = new ArrayList<>();
        for (Future<Optional<String>> update : updates) {
            update.get().ifPresent(savedFirstNames::add);
        }
        Map<String, Object> row = jdbcTemplate.queryForMap("SELECT first_name, version FROM candidate WHERE user_id = ?", userId);
        assertThat(savedFirstNames).hasSize(1).containsExactly((String) row.get("first_name"));
        assertThat(row.get("version")).isEqualTo(1L);
    }

    private Optional<String> savedFirstNameUnlessConflict(UUID userId, String firstName) {
        try {
            CandidateDetails details = new CandidateDetails(firstName, "Nowak", null, null, null, null);
            return Optional.of(candidateProfileService.updateProfile(userId, details, 0).firstName());
        } catch (ObjectOptimisticLockingFailureException conflict) {
            return Optional.empty();
        }
    }
}
