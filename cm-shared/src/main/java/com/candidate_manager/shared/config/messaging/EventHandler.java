package com.candidate_manager.shared.config.messaging;

public interface EventHandler<T> {

    void handle(T event);
}
