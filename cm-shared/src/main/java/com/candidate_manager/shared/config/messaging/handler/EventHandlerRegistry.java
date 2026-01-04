package com.candidate_manager.shared.config.messaging.handler;

import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class EventHandlerRegistry {

    private final Map<Class<?>, HandlerDefinition<?>> handlers = new HashMap<>();

    public EventHandlerRegistry(List<EventHandler<?>> handlerBeans) {
        handlerBeans.forEach(handler -> {
            RabbitEventHandler handlerAnnotation = handler.getClass().getAnnotation(RabbitEventHandler.class);
            if (Objects.isNull(handlerAnnotation)) {
                throw new IllegalArgumentException("Handler %s must be annotated with @RabbitEventHandler".formatted(handler.getClass().getName()));
            }

            boolean isIdempotent = handler.getClass().isAnnotationPresent(Idempotent.class);
            handlers.put(handlerAnnotation.value(), new HandlerDefinition<>(handler, isIdempotent));
        });
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<HandlerDefinition<T>> getHandlerDefinition(Class<T> eventType) {
        return Optional.ofNullable((HandlerDefinition<T>) handlers.get(eventType)
        );
    }
}
