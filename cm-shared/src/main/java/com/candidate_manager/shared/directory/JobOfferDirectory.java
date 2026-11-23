package com.candidate_manager.shared.directory;

import java.util.Optional;
import java.util.UUID;

public interface JobOfferDirectory {

    Optional<ActiveJobOffer> findActiveOffer(UUID jobOfferId);
}
