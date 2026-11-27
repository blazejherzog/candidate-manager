package com.candidate_manager.shared.directory;

import java.util.UUID;

public interface JobApplicationDirectory {

    boolean isVisibleToCandidate(UUID jobApplicationId, UUID userId);

    boolean isVisibleToRecruiter(UUID jobApplicationId, UUID recruiterId);
}
