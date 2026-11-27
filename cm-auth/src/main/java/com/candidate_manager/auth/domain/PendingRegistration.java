package com.candidate_manager.auth.domain;

import java.time.Instant;
import java.util.UUID;

public record PendingRegistration(UUID id, Instant createdAt) {
}
