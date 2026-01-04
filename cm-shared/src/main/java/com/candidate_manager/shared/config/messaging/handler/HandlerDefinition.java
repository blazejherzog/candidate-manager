package com.candidate_manager.shared.config.messaging.handler;

public record HandlerDefinition<T>(
        EventHandler<T> handler,
        boolean idempotent) {
}
