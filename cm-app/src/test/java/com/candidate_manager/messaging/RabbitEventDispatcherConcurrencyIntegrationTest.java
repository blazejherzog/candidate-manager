package com.candidate_manager.messaging;

import com.candidate_manager.TestcontainersConfiguration;
import com.candidate_manager.shared.config.messaging.handler.RabbitEventDispatcher;
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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RabbitEventDispatcherConcurrencyIntegrationTest {

    private static final int THREADS = 2;

    @Autowired
    private RabbitEventDispatcher dispatcher;

    @Autowired
    private TestIdempotentEventHandler handler;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void createHandlerEffectTable() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS test_handler_effect (event_id UUID NOT NULL)");
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS test_handler_effect");
        jdbcTemplate.update("DELETE FROM processed_event");
    }

    @Test
    void handlesEventOnlyOnceWhenTheSameEventIsDispatchedConcurrently() throws Exception {
        TestIdempotentEvent event = new TestIdempotentEvent(UUID.randomUUID());
        handler.expectConcurrentCalls(THREADS);

        CountDownLatch startSignal = new CountDownLatch(1);
        Callable<Void> dispatchOnSignal = () -> {
            startSignal.await();
            dispatcher.dispatch(event);
            return null;
        };

        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        try {
            List<Future<Void>> results = List.of(
                    executor.submit(dispatchOnSignal),
                    executor.submit(dispatchOnSignal));

            startSignal.countDown();

            for (Future<Void> result : results) {
                result.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(countRows("test_handler_effect", event)).isEqualTo(1);
        assertThat(countRows("processed_event", event)).isEqualTo(1);
    }

    private int countRows(String table, TestIdempotentEvent event) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE event_id = ?", Integer.class, event.eventId());
    }
}
