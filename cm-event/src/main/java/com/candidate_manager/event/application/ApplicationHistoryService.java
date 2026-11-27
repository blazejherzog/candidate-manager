package com.candidate_manager.event.application;

import com.candidate_manager.event.domain.ApplicationHistoryEntry;
import com.candidate_manager.event.domain.ApplicationHistoryRepository;
import com.candidate_manager.shared.directory.JobApplicationDirectory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationHistoryService {

    private final ApplicationHistoryRepository applicationHistoryRepository;
    private final JobApplicationDirectory jobApplicationDirectory;

    @Transactional
    public void record(ApplicationHistoryEntry entry) {
        applicationHistoryRepository.save(entry);
        log.info("Recorded version {} of application {} in history", entry.getApplicationVersion(), entry.getJobApplicationId());
    }

    @Transactional(readOnly = true)
    public List<ApplicationHistoryEntryView> findHistoryForCandidate(UUID jobApplicationId, UUID userId) {
        if (!jobApplicationDirectory.isVisibleToCandidate(jobApplicationId, userId)) {
            throw new JobApplicationNotVisibleException();
        }
        return historyOf(jobApplicationId);
    }

    @Transactional(readOnly = true)
    public List<ApplicationHistoryEntryView> findHistoryForRecruiter(UUID jobApplicationId, UUID recruiterId) {
        if (!jobApplicationDirectory.isVisibleToRecruiter(jobApplicationId, recruiterId)) {
            throw new JobApplicationNotVisibleException();
        }
        return historyOf(jobApplicationId);
    }

    private List<ApplicationHistoryEntryView> historyOf(UUID jobApplicationId) {
        return applicationHistoryRepository.findByJobApplicationIdOrderByApplicationVersion(jobApplicationId).stream()
                .map(ApplicationHistoryEntryView::from)
                .toList();
    }
}
