package com.candidate_manager.shared.directory;

import java.util.UUID;

public record ActiveJobOffer(UUID id, UUID recruiterId, String title, String companyName) {
}
