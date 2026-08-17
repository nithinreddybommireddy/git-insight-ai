package com.gitinsight.githubservice.service;

import com.gitinsight.githubservice.dto.response.DeveloperScoreResponse;
import com.gitinsight.githubservice.entity.ScoreHistory;
import com.gitinsight.githubservice.repository.ScoreHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Score-history persistence with per-owner scoping.
 *
 * <p>Every snapshot is attributed to the authenticated user who saved it
 * ({@code ownerId}). A USER can only read the snapshots they recorded
 * themselves — one user can never see, pollute, or mine another user's saved
 * reports. RECRUITERs and ADMINs are privileged: recruiter flows work on
 * candidate reports, and the admin/stats surfaces see everything (including
 * pre-ownership rows whose {@code ownerId} is null).
 */
@Service
public class ScoreHistoryService {

    private static final Logger log = LoggerFactory.getLogger(ScoreHistoryService.class);

    private final ScoreHistoryRepository repository;

    public ScoreHistoryService(ScoreHistoryRepository repository) {
        this.repository = repository;
    }

    /** {@code ownerId} is the auth-service user id of the requester (never null for new rows). */
    public ScoreHistory recordScore(DeveloperScoreResponse score, Long ownerId) {
        ScoreHistory history = new ScoreHistory();
        history.setOwnerId(ownerId);
        history.setUsername(score.getUsername());
        history.setOverallScore(score.getOverallScore());
        history.setLevel(score.getLevel());

        history.setContributionRecency(score.getContributionRecency());
        history.setCommitFrequency(score.getCommitFrequency());
        history.setRepositoryHealth(score.getRepositoryHealth());
        history.setRepositoryQuality(score.getRepositoryQuality());
        history.setContributionConsistency(score.getContributionConsistency());
        history.setLanguageDiversity(score.getLanguageDiversity());
        history.setCollaboration(score.getCollaboration());
        history.setOpenSourceImpact(score.getOpenSourceImpact());
        history.setPopularity(score.getPopularity());
        history.setMaintenance(score.getMaintenance());

        history.setTotalStars(score.getTotalStars());
        history.setTotalForks(score.getTotalForks());
        history.setTotalRepositories(score.getTotalRepositories());
        history.setLanguageCount(score.getLanguageCount());

        if (score.getLanguages() != null && score.getLanguages().length > 0) {
            history.setLanguages(String.join(", ", score.getLanguages()));
        }

        ScoreHistory saved = repository.save(history);
        log.info("Recorded score for {} (owner {}): {} (total snapshots: {})",
                score.getUsername(), ownerId, score.getOverallScore(),
                repository.countByUsername(score.getUsername()));

        return saved;
    }

    /**
     * Score history for a developer in chronological order (chart rendering).
     * Regular users only see their own snapshots for this username.
     */
    public List<ScoreHistory> getHistoryAscending(String username, Long ownerId, boolean privileged) {
        return privileged
                ? repository.findByUsernameOrderByCreatedAtAsc(username)
                : repository.findByOwnerIdAndUsernameOrderByCreatedAtAsc(ownerId, username);
    }

    /** Latest recorded score for a developer (scoped like {@link #getHistoryAscending}). */
    public ScoreHistory getLatest(String username, Long ownerId, boolean privileged) {
        return privileged
                ? repository.findTopByUsernameOrderByCreatedAtDesc(username).orElse(null)
                : repository.findTopByOwnerIdAndUsernameOrderByCreatedAtDesc(ownerId, username).orElse(null);
    }

    /**
     * All history across developers, paginated: own snapshots for USER,
     * everything for RECRUITER/ADMIN. Never loads the full table.
     */
    public Page<ScoreHistory> getAllHistory(Long ownerId, boolean privileged, Pageable pageable) {
        return privileged
                ? repository.findAllByOrderByCreatedAtDesc(pageable)
                : repository.findByOwnerIdOrderByCreatedAtDesc(ownerId, pageable);
    }

    /**
     * Statistics over the caller's visible snapshots, computed with SQL
     * aggregation (COUNT / AVG) so the whole table is never materialized.
     */
    public Map<String, Object> getStats(Long ownerId, boolean privileged) {
        long totalSnapshots;
        long uniqueUsers;
        double avg;

        if (privileged) {
            totalSnapshots = repository.countAll();
            uniqueUsers = repository.countDistinctUsernameAll();
            avg = orZero(repository.averageScoreAll());
        } else {
            totalSnapshots = repository.countByOwnerId(ownerId);
            uniqueUsers = repository.countDistinctUsernameByOwnerId(ownerId);
            avg = orZero(repository.averageScoreByOwnerId(ownerId));
        }

        return Map.of(
                "totalSnapshots", totalSnapshots,
                "uniqueUsers", uniqueUsers,
                "averageScore", (int) Math.round(avg)
        );
    }

    private static double orZero(Double value) {
        return value == null ? 0 : value;
    }
}
