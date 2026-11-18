package com.candidate_manager.messaging;

import com.candidate_manager.TestcontainersConfiguration;
import com.candidate_manager.shared.config.messaging.consumer.IdempotentConsumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class IdempotentConsumerIntegrationTest {

    private static final String CONSUMER = "test-consumer";
    private static final String OTHER_CONSUMER = "other-test-consumer";
    private static final int THREADS = 2;
    private static final long MAX_WAIT_FOR_OVERLAPPING_EFFECT_SECONDS = 2;

    @Autowired
    private IdempotentConsumer idempotentConsumer;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void createEffectTable() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS test_effect (event_id UUID NOT NULL, consumer VARCHAR(255) NOT NULL)");
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS test_effect");
        jdbcTemplate.update("DELETE FROM processed_event");
    }

    @Test
    void runsEffectOnceWhenTheSameEventIsConsumedConcurrently() throws Exception {
        UUID eventId = UUID.randomUUID();
        CountDownLatch effectsInside = new CountDownLatch(THREADS);
        CountDownLatch startSignal = new CountDownLatch(1);
        Callable<Boolean> consumeOnSignal = () -> {
            startSignal.await();
            return idempotentConsumer.runOnce(eventId, CONSUMER, () -> {
                recordEffect(eventId, CONSUMER);
                effectsInside.countDown();
                awaitQuietly(effectsInside);
            });
        };

        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        List<Boolean> results;
        try {
            List<Future<Boolean>> futures = List.of(executor.submit(consumeOnSignal), executor.submit(consumeOnSignal));
            startSignal.countDown();
            results = List.of(futures.get(0).get(10, TimeUnit.SECONDS), futures.get(1).get(10, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }

        assertThat(results).containsExactlyInAnyOrder(true, false);
        assertThat(countEffects(eventId, CONSUMER)).isEqualTo(1);
        assertThat(countProcessed(eventId, CONSUMER)).isEqualTo(1);
    }

    @Test
    void rollsBackProcessedMarkerWithFailedEffectSoRedeliveryIsProcessed() {
        UUID eventId = UUID.randomUUID();
        AtomicBoolean failNextEffect = new AtomicBoolean(true);
        Runnable effectFailingOnce = () -> {
            recordEffect(eventId, CONSUMER);
            if (failNextEffect.getAndSet(false)) {
                throw new IllegalStateException("Simulated effect failure");
            }
        };

        assertThatThrownBy(() -> idempotentConsumer.runOnce(eventId, CONSUMER, effectFailingOnce))
                .isInstanceOf(IllegalStateException.class);

        assertThat(countProcessed(eventId, CONSUMER)).isZero();
        assertThat(countEffects(eventId, CONSUMER)).isZero();

        assertThat(idempotentConsumer.runOnce(eventId, CONSUMER, effectFailingOnce)).isTrue();

        assertThat(countProcessed(eventId, CONSUMER)).isEqualTo(1);
        assertThat(countEffects(eventId, CONSUMER)).isEqualTo(1);
    }

    @Test
    void runsEffectOncePerConsumerOfTheSameEvent() {
        UUID eventId = UUID.randomUUID();

        boolean firstConsumer = idempotentConsumer.runOnce(eventId, CONSUMER, () -> recordEffect(eventId, CONSUMER));
        boolean otherConsumer = idempotentConsumer.runOnce(eventId, OTHER_CONSUMER, () -> recordEffect(eventId, OTHER_CONSUMER));
        boolean firstConsumerAgain = idempotentConsumer.runOnce(eventId, CONSUMER, () -> recordEffect(eventId, CONSUMER));

        assertThat(firstConsumer).isTrue();
        assertThat(otherConsumer).isTrue();
        assertThat(firstConsumerAgain).isFalse();
        assertThat(countEffects(eventId, CONSUMER)).isEqualTo(1);
        assertThat(countEffects(eventId, OTHER_CONSUMER)).isEqualTo(1);
    }

    private void recordEffect(UUID eventId, String consumer) {
        jdbcTemplate.update("INSERT INTO test_effect (event_id, consumer) VALUES (?, ?)", eventId, consumer);
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(MAX_WAIT_FOR_OVERLAPPING_EFFECT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private int countEffects(UUID eventId, String consumer) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM test_effect WHERE event_id = ? AND consumer = ?", Integer.class, eventId, consumer);
    }

    private int countProcessed(UUID eventId, String consumer) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM processed_event WHERE event_id = ? AND consumer = ?", Integer.class, eventId, consumer);
    }
}
