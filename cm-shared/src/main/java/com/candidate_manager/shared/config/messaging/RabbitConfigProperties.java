package com.candidate_manager.shared.config.messaging;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@ConfigurationProperties(prefix = "rabbitmq")
public class RabbitConfigProperties {

    private List<RabbitQueueDefinition> queues = new ArrayList<>();

    @Data
    public static class RabbitQueueDefinition {
        private String name;
        private String exchange;
        private String routingKey;
        private boolean durable = true;
    }
}
