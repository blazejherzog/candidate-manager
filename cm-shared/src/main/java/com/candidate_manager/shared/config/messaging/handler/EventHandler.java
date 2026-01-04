package com.candidate_manager.shared.config.messaging.handler;

public interface EventHandler<T> {

    void handle(T event);
}
