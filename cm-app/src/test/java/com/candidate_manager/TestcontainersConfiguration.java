package com.candidate_manager;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:15"));
    }

    @Bean
    @ServiceConnection
    RabbitMQContainer rabbitContainer() {
        return new RabbitMQContainer(DockerImageName.parse("rabbitmq:3-management"));
    }

    @Bean
    KeycloakTestContainer keycloakContainer() {
        return new KeycloakTestContainer();
    }

    @Bean
    DynamicPropertyRegistrar keycloakContainerProperties(KeycloakTestContainer keycloak) {
        return registry -> {
            registry.add("keycloak.url", keycloak::url);
            registry.add("keycloak.client-secret", keycloak::readGeneratedBackendClientSecret);
            registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", keycloak::issuerUri);
        };
    }
}
