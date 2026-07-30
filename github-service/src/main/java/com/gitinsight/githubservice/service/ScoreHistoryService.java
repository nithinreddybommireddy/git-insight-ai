package com.gitinsight.githubservice.service;

import com.gitinsight.githubservice.dto.response.DeveloperScoreResponse;
import com.gitinsight.githubservice.entity.ScoreHistory;
import com.gitinsight.githubservice.repository.ScoreHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ScoreHistoryService {

    private static final Logger log = LoggerFactory.getLogger(ScoreHistoryService.class);

    private final ScoreHistoryRepository repository;

    public ScoreHistoryService(ScoreHistoryRepository repository) {
        this.repository = repository;
    }

    /**
     * Record a score snapshot for a developer.
     */
    public ScoreHistory recordScore(DeveloperScoreResponse score) {
        ScoreHistory history = new ScoreHistory();
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
        log.info("Recorded score for {}: {} (total snapshots: {})",
                score.getUsername(), score.getOverallScore(),
                repository.countByUsername(score.getUsername()));

        return saved;
    }

    /**
     * Get score history for a specific developer (most recent first).
     */
    public List<ScoreHistory> getHistory(String username) {
        return repository.findByUsernameOrderByCreatedAtDesc(username);
    }

    /**
     * Get score history in chronological order for chart rendering.
     */
    public List<ScoreHistory> getHistoryAscending(String username) {
        return repository.findByUsernameOrderByCreatedAtAsc(username);
    }

    /**
     * Get the latest score for a developer.
     */
    public ScoreHistory getLatest(String username) {
        return repository.findTopByUsernameOrderByCreatedAtDesc(username).orElse(null);
    }

    /**
     * Get history for all developers (most recent first).
     */
    public List<ScoreHistory> getAllHistory() {
        return repository.findAllByOrderByCreatedAtDesc();
    }

    /**
     * Get statistics about stored scores.
     */
    public Map<String, Object> getStats() {
        List<ScoreHistory> all = repository.findAll();
        long totalSnapshots = all.size();
        long uniqueUsers = all.stream()
                .map(ScoreHistory::getUsername)
                .distinct()
                .count();

        double avg = all.stream()
                .mapToInt(ScoreHistory::getOverallScore)
                .average()
                .orElse(0);

        return Map.of(
                "totalSnapshots", totalSnapshots,
                "uniqueUsers", uniqueUsers,
                "averageScore", (int) Math.round(avg)
        );
    }
}
