package com.candidate_manager.messaging;

import com.candidate_manager.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RabbitDeadLetteringIntegrationTest {

    private static final String CANDIDATE_EXCHANGE = "candidate.exchange";
    private static final String CANDIDATE_CREATED_ROUTING_KEY = "candidate.created";
    private static final String CANDIDATE_CREATED_DEAD_LETTER_QUEUE = "candidate.created.queue.dlq";
    private static final String UNROUTABLE_QUEUE = "cm.unroutable.queue";

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @Test
    void movesMessageThatCannotBeProcessedToDeadLetterQueue() {
        rabbitAdmin.purgeQueue(CANDIDATE_CREATED_DEAD_LETTER_QUEUE, false);
        String messageId = UUID.randomUUID().toString();

        // Invalid JSON: the listener fails on every retry attempt, then the message is rejected.
        rabbitTemplate.send(CANDIDATE_EXCHANGE, CANDIDATE_CREATED_ROUTING_KEY, jsonMessage(messageId, "not a json"));

        Message deadLettered = rabbitTemplate.receive(CANDIDATE_CREATED_DEAD_LETTER_QUEUE, 15_000);
        assertThat(deadLettered).isNotNull();
        assertThat(deadLettered.getMessageProperties().getMessageId()).isEqualTo(messageId);
    }

    @Test
    void routesMessageWithoutMatchingQueueToUnroutableQueue() {
        rabbitAdmin.purgeQueue(UNROUTABLE_QUEUE, false);
        String messageId = UUID.randomUUID().toString();

        rabbitTemplate.send(CANDIDATE_EXCHANGE, "no.queue.bound", jsonMessage(messageId, "{}"));

        Message unroutable = rabbitTemplate.receive(UNROUTABLE_QUEUE, 5_000);
        assertThat(unroutable).isNotNull();
        assertThat(unroutable.getMessageProperties().getMessageId()).isEqualTo(messageId);
    }

    private Message jsonMessage(String messageId, String body) {
        return MessageBuilder.withBody(body.getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setMessageId(messageId)
                .build();
    }
}
