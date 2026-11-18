package com.candidate_manager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.rabbitmq.host=localhost",
        "spring.rabbitmq.port=1",
        "keycloak.client-secret=unused-in-this-test"
})
@Import(PostgresOnlyTestcontainersConfiguration.class)
class ApplicationStartsWithoutRabbitMqIntegrationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void startsWhenRabbitMqIsUnavailable() {
        assertThat(applicationContext).isNotNull();
    }
}
