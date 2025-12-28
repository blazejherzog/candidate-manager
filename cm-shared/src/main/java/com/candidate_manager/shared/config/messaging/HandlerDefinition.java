package com.candidate_manager.shared.config.messaging;

public record HandlerDefinition<T>(
        EventHandler<T> handler,
        boolean idempotent) {
}
