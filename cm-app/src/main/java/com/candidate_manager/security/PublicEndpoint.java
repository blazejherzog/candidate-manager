package com.candidate_manager.security;

import org.springframework.http.HttpMethod;

import java.util.List;

public record PublicEndpoint(HttpMethod method, String path) {

    public static final List<PublicEndpoint> ALL = List.of(
            new PublicEndpoint(HttpMethod.POST, "/api/auth/register/candidate"),
            new PublicEndpoint(HttpMethod.POST, "/api/auth/register/recruiter"),
            new PublicEndpoint(HttpMethod.GET, "/api/job-offers"),
            new PublicEndpoint(HttpMethod.GET, "/api/job-offers/{id}"));

    String requestMatcherPattern() {
        return path.replaceAll("\\{[^}]+}", "*");
    }
}
