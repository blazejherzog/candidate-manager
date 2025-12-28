package com.candidate_manager.shared.config.messaging;

import org.springframework.stereotype.Component;

import java.lang.annotation.*;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface RabbitEventHandler {
    Class<?> value();
}
