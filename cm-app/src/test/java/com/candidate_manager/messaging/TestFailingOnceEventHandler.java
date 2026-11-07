package com.candidate_manager.messaging;

import com.candidate_manager.shared.config.messaging.handler.EventHandler;
import com.candidate_manager.shared.config.messaging.handler.Idempotent;
import com.candidate_manager.shared.config.messaging.handler.RabbitEventHandler;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.concurrent.atomic.AtomicBoolean;

@RabbitEventHandler(TestFailingOnceEvent.class)
@Idempotent
public class TestFailingOnceEventHandler implements EventHandler<TestFailingOnceEvent> {

    private final JdbcTemplate jdbcTemplate;
    private final AtomicBoolean failNextCall = new AtomicBoolean();

    public TestFailingOnceEventHandler(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void failNextCall() {
        failNextCall.set(true);
    }

    @Override
    public void handle(TestFailingOnceEvent event) {
        // The effect is written before the failure so the test can prove it is rolled back too.
        jdbcTemplate.update("INSERT INTO test_handler_effect (event_id) VALUES (?)", event.eventId());

        if (failNextCall.getAndSet(false)) {
            throw new IllegalStateException("Simulated handler failure");
        }
    }
}
