package com.candidate_manager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ApplicationStartupIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void appliesAllFlywayMigrationsSuccessfully() {
        List<String> appliedVersions = jdbcTemplate.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank",
                String.class);

        assertThat(appliedVersions).contains("1");
    }

    @Test
    void createsProcessedEventTable() {
        Integer rowCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM processed_event", Integer.class);

        assertThat(rowCount).isZero();
    }
}
