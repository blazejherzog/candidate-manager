package com.candidate_manager.job.web;

import com.candidate_manager.job.domain.JobOffer;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.LocalDate;

class WithinMaxValidityValidator implements ConstraintValidator<WithinMaxValidity, LocalDate> {

    @Override
    public boolean isValid(LocalDate validUntil, ConstraintValidatorContext context) {
        LocalDate today = LocalDate.now(context.getClockProvider().getClock());
        return validUntil == null || !validUntil.isAfter(today.plus(JobOffer.MAX_VALIDITY));
    }
}
