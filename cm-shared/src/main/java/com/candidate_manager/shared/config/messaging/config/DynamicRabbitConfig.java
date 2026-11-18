package com.candidate_manager.shared.config.messaging.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableConfigurationProperties(RabbitConfigProperties.class)
public class DynamicRabbitConfig {

    public static final String DEAD_LETTER_EXCHANGE = "cm.dlx";
    public static final String DEAD_LETTER_QUEUE_SUFFIX = ".dlq";
    public static final String UNROUTABLE_EXCHANGE = "cm.unroutable";
    public static final String UNROUTABLE_QUEUE = "cm.unroutable.queue";

    @Bean
    Declarables messagingTopologyDeclaredOnFirstBrokerConnection(RabbitConfigProperties properties) {
        List<Declarable> declarables = new ArrayList<>(unroutableMessagesTopology());

        DirectExchange deadLetterExchange = new DirectExchange(DEAD_LETTER_EXCHANGE);
        declarables.add(deadLetterExchange);

        properties.getQueues().forEach(queueDefinition ->
                declarables.addAll(queueWithDeadLetterQueue(queueDefinition, deadLetterExchange)));

        return new Declarables(declarables);
    }

    private static List<Declarable> unroutableMessagesTopology() {
        FanoutExchange unroutableExchange = new FanoutExchange(UNROUTABLE_EXCHANGE);
        Queue unroutableQueue = QueueBuilder.durable(UNROUTABLE_QUEUE).build();
        return List.of(unroutableExchange, unroutableQueue, BindingBuilder.bind(unroutableQueue).to(unroutableExchange));
    }

    private static List<Declarable> queueWithDeadLetterQueue(RabbitConfigProperties.RabbitQueueDefinition queueDefinition,
                                                             DirectExchange deadLetterExchange) {
        String deadLetterQueueName = queueDefinition.getName() + DEAD_LETTER_QUEUE_SUFFIX;
        Queue deadLetterQueue = QueueBuilder.durable(deadLetterQueueName).build();
        Binding deadLetterBinding = BindingBuilder.bind(deadLetterQueue)
                .to(deadLetterExchange)
                .with(deadLetterQueueName);

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

        return List.of(deadLetterQueue, deadLetterBinding, queue, topicExchange, binding);
    }
}
