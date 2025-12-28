package com.candidate_manager.shared.config.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RabbitEventDispatcher {

    private final EventHandlerRegistry eventHandlerRegistry;
    private final ProcessedEventRepository processedEventRepository;

    public <T> void dispatch(T event) {
        Class<T> eventClass = getEventClass(event);

        HandlerDefinition<T> handlerDefinition = eventHandlerRegistry.getHandlerDefinition(eventClass)
                .orElseThrow(() -> new IllegalArgumentException("No handlerDefinition found for event %s".formatted(eventClass.getName())));

        EventHandler<T> eventHandler = handlerDefinition.handler();

        if (handlerDefinition.idempotent()) {
            if (!(event instanceof DomainEvent domainEvent)) {
                throw new IllegalArgumentException("Idempotent event must be of type DomainEvent");
            }

            if (processedEventRepository.existsByEventId(domainEvent.getEventId())) {
                return;
            }

            eventHandler.handle(event);
            processedEventRepository.save(domainEvent.getEventId());
        } else {
            eventHandler.handle(event);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> Class<T> getEventClass(T event) {
        return (Class<T>) event.getClass();
    }
}
