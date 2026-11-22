package com.candidate_manager.job;

import com.candidate_manager.TestcontainersConfiguration;
import com.candidate_manager.job.application.JobOfferService;
import com.candidate_manager.job.domain.ContractType;
import com.candidate_manager.job.domain.EmploymentTime;
import com.candidate_manager.job.domain.JobCategory;
import com.candidate_manager.job.domain.JobOfferContent;
import com.candidate_manager.job.domain.JobOfferNotDraftException;
import com.candidate_manager.job.domain.PositionLevel;
import com.candidate_manager.job.domain.Salary;
import com.candidate_manager.job.domain.SalaryCurrency;
import com.candidate_manager.job.domain.SalaryPeriod;
import com.candidate_manager.job.domain.WorkMode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class JobOfferPublicationConcurrencyIntegrationTest {

    private static final int CONCURRENT_PUBLICATIONS = 8;

    @Autowired
    private JobOfferService jobOfferService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void publishesDraftExactlyOnceWhenPublishedConcurrently() throws Exception {
        UUID recruiterId = UUID.randomUUID();
        UUID offerId = jobOfferService.createDraft(recruiterId, draftContent()).id();
        CountDownLatch start = new CountDownLatch(1);
        List<Future<PublicationOutcome>> outcomes = new ArrayList<>();

        try (ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_PUBLICATIONS)) {
            for (int i = 0; i < CONCURRENT_PUBLICATIONS; i++) {
                outcomes.add(executor.submit(() -> {
                    start.await();
                    return publish(offerId, recruiterId);
                }));
            }
            start.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }

        List<PublicationOutcome> results = new ArrayList<>();
        for (Future<PublicationOutcome> outcome : outcomes) {
            results.add(outcome.get());
        }
        assertThat(results).filteredOn(PublicationOutcome.PUBLISHED::equals).hasSize(1);
        assertThat(results).filteredOn(PublicationOutcome.REJECTED_AS_CONFLICT::equals).hasSize(CONCURRENT_PUBLICATIONS - 1);
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT status, version, published_at FROM job_offer WHERE id = ?", offerId);
        assertThat(row.get("status")).isEqualTo("PUBLISHED");
        assertThat(row.get("version")).isEqualTo(1L);
        assertThat(row.get("published_at")).isNotNull();
    }

    private PublicationOutcome publish(UUID offerId, UUID recruiterId) {
        try {
            jobOfferService.publish(offerId, recruiterId);
            return PublicationOutcome.PUBLISHED;
        } catch (ObjectOptimisticLockingFailureException | JobOfferNotDraftException e) {
            return PublicationOutcome.REJECTED_AS_CONFLICT;
        }
    }

    private static JobOfferContent draftContent() {
        return new JobOfferContent(
                "Warehouse Shift Manager",
                "Logistics Partner S.A.",
                JobCategory.LOGISTICS,
                PositionLevel.MANAGER,
                WorkMode.OFFICE,
                EmploymentTime.FULL_TIME,
                List.of("Poznań"),
                List.of(new Salary(ContractType.EMPLOYMENT, new BigDecimal("9000.00"), new BigDecimal("11000.00"),
                        SalaryCurrency.PLN, SalaryPeriod.MONTH)),
                "Coordinating a team of 20 warehouse operators.",
                List.of("Planning shifts"),
                List.of("Two years of team management"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                null);
    }

    private enum PublicationOutcome {
        PUBLISHED,
        REJECTED_AS_CONFLICT
    }
}
