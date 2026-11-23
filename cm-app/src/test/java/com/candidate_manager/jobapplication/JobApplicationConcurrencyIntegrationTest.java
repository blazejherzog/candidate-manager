package com.candidate_manager.jobapplication;

import com.candidate_manager.TestcontainersConfiguration;
import com.candidate_manager.jobapplication.application.AlreadyAppliedException;
import com.candidate_manager.jobapplication.application.JobApplicationService;
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
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Import({TestcontainersConfiguration.class, JobApplicationTestData.class})
class JobApplicationConcurrencyIntegrationTest {

    private static final int CONCURRENT_REQUESTS = 4;
    private static final String UPDATES_WAITING_FOR_LOCK = """
            SELECT COUNT(*) FROM pg_stat_activity
            WHERE datname = current_database() AND wait_event_type = 'Lock' AND query LIKE 'update job_application %'
            """;

    @Autowired
    private JobApplicationService jobApplicationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JobApplicationTestData testData;

    @Test
    void storesExactlyOneApplicationWhenCandidateAppliesConcurrently() throws Exception {
        UUID offerId = testData.publishedOffer();
        UUID userId = testData.registeredCandidate();
        List<Future<Outcome>> outcomes = new ArrayList<>();

        try (ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS)) {
            submitConcurrently(executor, outcomes, () -> {
                jobApplicationService.apply(offerId, userId, null);
                return Outcome.ACCEPTED;
            });
        }

        assertExactlyOneAccepted(outcomes);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM job_application WHERE job_offer_id = ?", Integer.class, offerId)).isEqualTo(1);
    }

    @Test
    void withdrawsExactlyOnceWhenWithdrawnConcurrently() throws Exception {
        UUID userId = testData.registeredCandidate();
        UUID applicationId = jobApplicationService.apply(testData.publishedOffer(), userId, null).id();
        List<Future<Outcome>> outcomes = new ArrayList<>();

        try (ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS)) {
            transactionTemplate.executeWithoutResult(transactionHoldingRowLock -> {
                jdbcTemplate.queryForMap("SELECT id FROM job_application WHERE id = ? FOR UPDATE", applicationId);
                submitConcurrently(executor, outcomes, () -> {
                    jobApplicationService.withdraw(applicationId, userId);
                    return Outcome.ACCEPTED;
                });
                await().atMost(Duration.ofSeconds(10)).until(() ->
                        jdbcTemplate.queryForObject(UPDATES_WAITING_FOR_LOCK, Integer.class) == CONCURRENT_REQUESTS);
            });
        }

        assertExactlyOneAccepted(outcomes);
        Map<String, Object> row = jdbcTemplate.queryForMap("SELECT status, version FROM job_application WHERE id = ?", applicationId);
        assertThat(row).containsEntry("status", "WITHDRAWN").containsEntry("version", 1L);
    }

    private static void submitConcurrently(ExecutorService executor, List<Future<Outcome>> outcomes, Callable<Outcome> request) {
        CountDownLatch start = new CountDownLatch(1);
        for (int requestNumber = 0; requestNumber < CONCURRENT_REQUESTS; requestNumber++) {
            outcomes.add(executor.submit(() -> {
                start.await();
                return outcomeOf(request);
            }));
        }
        start.countDown();
    }

    private static Outcome outcomeOf(Callable<Outcome> request) throws Exception {
        try {
            return request.call();
        } catch (AlreadyAppliedException | ObjectOptimisticLockingFailureException conflict) {
            return Outcome.REJECTED_AS_CONFLICT;
        }
    }

    private static void assertExactlyOneAccepted(List<Future<Outcome>> outcomes) throws Exception {
        List<Outcome> results = new ArrayList<>();
        for (Future<Outcome> outcome : outcomes) {
            results.add(outcome.get(30, TimeUnit.SECONDS));
        }
        assertThat(results).filteredOn(Outcome.ACCEPTED::equals).hasSize(1);
        assertThat(results).filteredOn(Outcome.REJECTED_AS_CONFLICT::equals).hasSize(CONCURRENT_REQUESTS - 1);
    }

    private enum Outcome {
        ACCEPTED,
        REJECTED_AS_CONFLICT
    }
}
