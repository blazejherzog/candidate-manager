package com.candidate_manager.shared.config.messaging.outbox;

import com.rabbitmq.client.ShutdownSignalException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxRelay {

    @Value("${outbox.relay.batch-size}")
    private int batchSize;

    @Value("${outbox.relay.confirm-timeout}")
    private long confirmTimeoutMillis;

    private final OutboxEventJpaRepository outboxEventJpaRepository;
    private final RabbitTemplate rabbitTemplate;

    @Transactional
    public int publishPendingEvents() {
        int counter = 0;
        List<OutboxEventEntity> pendingEvents = outboxEventJpaRepository.findPendingForUpdate(batchSize);
        for (OutboxEventEntity outboxEvent : pendingEvents) {
            try {
                log.debug("Publishing outbox event: {}", outboxEvent.getId());
                sendAndWaitForConfirmation(outboxEvent);
            } catch (AmqpException | ShutdownSignalException e) {
                log.error("Failed to publish outbox event {}, stopping batch; will retry on next run", outboxEvent.getId(), e);
                break;
            }
            outboxEventJpaRepository.markPublished(outboxEvent.getId());
            counter++;
        }
        return counter;
    }

    private void sendAndWaitForConfirmation(OutboxEventEntity outboxEvent) {
        Message message = MessageBuilder.withBody(outboxEvent.getPayload().getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setMessageId(outboxEvent.getId().toString())
                .build();

        rabbitTemplate.invoke(operations -> {
            operations.send(outboxEvent.getExchange(), outboxEvent.getRoutingKey(), message);
            operations.waitForConfirmsOrDie(confirmTimeoutMillis);
            return null;
        });
    }
}
