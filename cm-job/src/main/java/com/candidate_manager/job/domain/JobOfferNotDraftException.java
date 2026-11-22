package com.candidate_manager.job.domain;

import java.util.UUID;

public class JobOfferNotDraftException extends RuntimeException {

    public JobOfferNotDraftException(UUID jobOfferId) {
        super("Job offer %s is already published and can no longer be changed".formatted(jobOfferId));
    }
}
