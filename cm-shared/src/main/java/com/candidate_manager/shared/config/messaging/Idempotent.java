package com.candidate_manager.shared.config.messaging;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {
}
