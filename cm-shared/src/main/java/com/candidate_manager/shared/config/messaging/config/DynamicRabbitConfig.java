package com.candidate_manager.shared.config.messaging.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RabbitConfigProperties.class)
@RequiredArgsConstructor
public class DynamicRabbitConfig {

    public static final String DEAD_LETTER_EXCHANGE = "cm.dlx";
    public static final String DEAD_LETTER_QUEUE_SUFFIX = ".dlq";
    public static final String UNROUTABLE_EXCHANGE = "cm.unroutable";
    public static final String UNROUTABLE_QUEUE = "cm.unroutable.queue";

    private final RabbitConfigProperties properties;
    private final RabbitAdmin rabbitAdmin;

    @PostConstruct
    public void declareQueuesExchangesAndBindings() {
        declareUnroutableMessagesQueue();

        DirectExchange deadLetterExchange = new DirectExchange(DEAD_LETTER_EXCHANGE);
        rabbitAdmin.declareExchange(deadLetterExchange);

        properties.getQueues().forEach(queueDefinition -> {
            String deadLetterQueueName = queueDefinition.getName() + DEAD_LETTER_QUEUE_SUFFIX;
            Queue deadLetterQueue = QueueBuilder.durable(deadLetterQueueName).build();

            QueueBuilder queueBuilder = queueDefinition.isDurable()
                    ? QueueBuilder.durable(queueDefinition.getName())
                    : QueueBuilder.nonDurable(queueDefinition.getName());
            Queue queue = queueBuilder
                    .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                    .deadLetterRoutingKey(deadLetterQueueName)
                    .build();

            TopicExchange topicExchange = ExchangeBuilder.topicExchange(queueDefinition.getExchange())
                    .durable(true)
                    .alternate(UNROUTABLE_EXCHANGE)
                    .build();
            Binding binding = BindingBuilder.bind(queue)
                    .to(topicExchange)
                    .with(queueDefinition.getRoutingKey());

            rabbitAdmin.declareQueue(deadLetterQueue);
            rabbitAdmin.declareBinding(BindingBuilder.bind(deadLetterQueue)
                    .to(deadLetterExchange)
                    .with(deadLetterQueueName));

            rabbitAdmin.declareQueue(queue);
            rabbitAdmin.declareExchange(topicExchange);
            rabbitAdmin.declareBinding(binding);
        });
    }

    private void declareUnroutableMessagesQueue() {
        FanoutExchange unroutableExchange = new FanoutExchange(UNROUTABLE_EXCHANGE);
        Queue unroutableQueue = QueueBuilder.durable(UNROUTABLE_QUEUE).build();

        rabbitAdmin.declareExchange(unroutableExchange);
        rabbitAdmin.declareQueue(unroutableQueue);
        rabbitAdmin.declareBinding(BindingBuilder.bind(unroutableQueue).to(unroutableExchange));
    }
}
