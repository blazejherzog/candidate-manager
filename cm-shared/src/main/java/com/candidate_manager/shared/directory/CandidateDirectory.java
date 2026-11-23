package com.candidate_manager.shared.directory;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface CandidateDirectory {

    Optional<UUID> findCandidateIdOfUser(UUID userId);

    Map<UUID, CandidateContact> findContactsByCandidateId(Collection<UUID> candidateIds);
}
