package com.candidate_manager.job.domain;

import java.time.LocalDate;
import java.util.UUID;

public class JobOfferValidityExpiredException extends RuntimeException {

    public JobOfferValidityExpiredException(UUID jobOfferId, LocalDate validUntil) {
        super("Job offer %s is valid only until %s; set a later validUntil before publishing"
                .formatted(jobOfferId, validUntil));
    }
}
