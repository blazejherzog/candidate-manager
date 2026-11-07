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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RabbitEventDispatcherRollbackIntegrationTest {

    @Autowired
    private RabbitEventDispatcher dispatcher;

    @Autowired
    private TestFailingOnceEventHandler handler;

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
    void rollsBackProcessedMarkerWhenHandlerFailsSoRedeliveryIsProcessed() {
        TestFailingOnceEvent event = new TestFailingOnceEvent(UUID.randomUUID());
        handler.failNextCall();

        assertThatThrownBy(() -> dispatcher.dispatch(event)).isInstanceOf(IllegalStateException.class);

        assertThat(countRows("processed_event", event)).isZero();
        assertThat(countRows("test_handler_effect", event)).isZero();

        dispatcher.dispatch(event);

        assertThat(countRows("processed_event", event)).isEqualTo(1);
        assertThat(countRows("test_handler_effect", event)).isEqualTo(1);
    }

    private int countRows(String table, TestFailingOnceEvent event) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE event_id = ?", Integer.class, event.eventId());
    }
}
