package com.candidate_manager.auth.infrastructure;

import com.candidate_manager.auth.domain.PendingRegistration;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PendingRegistrationRepository {

    private final JdbcTemplate jdbcTemplate;

    public void insert(UUID registrationId, Instant createdAt) {
        jdbcTemplate.update("INSERT INTO pending_registration (id, created_at) VALUES (?, ?)",
                registrationId, Timestamp.from(createdAt));
    }

    public boolean delete(UUID registrationId) {
        return jdbcTemplate.update("DELETE FROM pending_registration WHERE id = ?", registrationId) == 1;
    }

    public boolean lock(UUID registrationId) {
        return !jdbcTemplate.queryForList("SELECT id FROM pending_registration WHERE id = ? FOR UPDATE",
                UUID.class, registrationId).isEmpty();
    }

    public List<PendingRegistration> lockStartedBefore(Instant startedBefore, int limit) {
        return jdbcTemplate.query("""
                        SELECT id, created_at FROM pending_registration
                        WHERE created_at < ?
                        ORDER BY created_at
                        LIMIT ?
                        FOR UPDATE SKIP LOCKED
                        """,
                (row, rowNumber) -> new PendingRegistration(
                        row.getObject("id", UUID.class), row.getTimestamp("created_at").toInstant()),
                Timestamp.from(startedBefore), limit);
    }
}
