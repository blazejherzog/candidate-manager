package com.candidate_manager.shared.config.messaging;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RabbitConfigProperties.class)
@RequiredArgsConstructor
public class DynamicRabbitConfig {

    private final RabbitConfigProperties properties;
    private final RabbitAdmin rabbitAdmin;

    @PostConstruct
    public void declareQueuesExchangesAndBindings() {
        properties.getQueues().forEach(queueDefinition -> {
            Queue queue = new Queue(queueDefinition.getName(), queueDefinition.isDurable());
            TopicExchange topicExchange = new TopicExchange(queueDefinition.getExchange());
            Binding binding = BindingBuilder.bind(queue)
                    .to(topicExchange)
                    .with(queueDefinition.getRoutingKey());

            rabbitAdmin.declareQueue(queue);
            rabbitAdmin.declareExchange(topicExchange);
            rabbitAdmin.declareBinding(binding);
        });

    }
}
