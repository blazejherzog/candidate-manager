package com.candidate_manager.web;

import com.candidate_manager.security.CorsProperties;
import com.candidate_manager.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

class CorsDefaultsTest {

    @Test
    void allowsNoOriginWhenNoneIsConfigured() {
        CorsConfiguration configuration = new SecurityConfig()
                .corsConfigurationSource(new CorsProperties(null))
                .getCorsConfiguration(new MockHttpServletRequest("GET", "/api/job-offers"));

        assertThat(configuration).isNotNull();
        assertThat(configuration.checkOrigin("http://localhost:5173")).isNull();
    }
}
