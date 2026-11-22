package com.candidate_manager.shared.config.time;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.validation.ValidationConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class BusinessClockConfig {

    @Bean
    Clock businessClock(@Value("${app.time-zone}") ZoneId businessTimeZone) {
        return Clock.system(businessTimeZone);
    }

    @Bean
    ValidationConfigurationCustomizer validationUsesBusinessClock(Clock businessClock) {
        return configuration -> configuration.clockProvider(() -> businessClock);
    }
}
