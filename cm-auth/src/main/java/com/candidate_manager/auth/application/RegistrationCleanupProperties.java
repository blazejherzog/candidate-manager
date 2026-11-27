package com.candidate_manager.auth.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "registration.cleanup")
public record RegistrationCleanupProperties(Duration staleAfter, Duration abandonAfter, int batchSize) {
}
