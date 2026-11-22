package com.candidate_manager.job.web;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = WithinMaxValidityValidator.class)
@interface WithinMaxValidity {

    String message() default "must be at most 60 days from today";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
