package com.candidate_manager.shared.directory;

import java.util.Optional;
import java.util.UUID;

public interface CandidateDirectory {

    Optional<UUID> findCandidateIdOfUser(UUID userId);
}
