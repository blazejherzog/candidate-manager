package com.candidate_manager.messaging;

import com.candidate_manager.shared.config.messaging.handler.EventHandler;
import com.candidate_manager.shared.config.messaging.handler.Idempotent;
import com.candidate_manager.shared.config.messaging.handler.RabbitEventHandler;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RabbitEventHandler(TestIdempotentEvent.class)
@Idempotent
public class TestIdempotentEventHandler implements EventHandler<TestIdempotentEvent> {

    private static final long MAX_WAIT_FOR_OTHER_HANDLERS_SECONDS = 2;

    private final JdbcTemplate jdbcTemplate;
    private volatile CountDownLatch handlersInside = new CountDownLatch(0);

    public TestIdempotentEventHandler(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void expectConcurrentCalls(int count) {
        handlersInside = new CountDownLatch(count);
    }

    @Override
    public void handle(TestIdempotentEvent event) {
        jdbcTemplate.update("INSERT INTO test_handler_effect (event_id) VALUES (?)", event.eventId());

        // Keeps the transaction open so concurrent dispatches overlap. Bounded wait: with a correct
        // dispatcher the second thread blocks on the processed_event insert and never gets here.
        handlersInside.countDown();
        try {
            handlersInside.await(MAX_WAIT_FOR_OTHER_HANDLERS_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
