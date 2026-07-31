package com.gitinsight.githubservice.controller;

import com.gitinsight.common.dto.response.ApiResponse;
import com.gitinsight.githubservice.dto.response.DeveloperScoreResponse;
import com.gitinsight.githubservice.dto.response.GitHubProfileResponse;
import com.gitinsight.githubservice.dto.response.RepositoryResponse;
import com.gitinsight.githubservice.entity.ScoreHistory;
import com.gitinsight.githubservice.service.GitHubIntegrationService;
import com.gitinsight.githubservice.service.GitHubService;
import com.gitinsight.githubservice.service.ScoreHistoryService;
import com.gitinsight.githubservice.service.ScoringEngine;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reports")
public class ReportsController {

    private final ScoreHistoryService scoreHistoryService;
    private final GitHubService gitHubService;
    private final ScoringEngine scoringEngine;
    private final GitHubIntegrationService integrationService;

    public ReportsController(ScoreHistoryService scoreHistoryService,
                              GitHubService gitHubService,
                              ScoringEngine scoringEngine,
                              GitHubIntegrationService integrationService) {
        this.scoreHistoryService = scoreHistoryService;
        this.gitHubService = gitHubService;
        this.scoringEngine = scoringEngine;
        this.integrationService = integrationService;
    }

    /**
     * Generate and record a new score snapshot for the given user.
     */
    @PostMapping("/record/{username}")
    public ApiResponse<ScoreHistory> recordScore(@PathVariable String username) {
        List<RepositoryResponse> repos = gitHubService.getRepositories(username);
        GitHubProfileResponse profile = gitHubService.getProfile(username);
        GitHubIntegrationService.EnrichedScoreData enriched = integrationService.getEnrichedScoreData(repos);
        DeveloperScoreResponse score = scoringEngine.calculate(username, repos, profile,
                enriched.weightedLanguages(), enriched.contributors());

        ScoreHistory recorded = scoreHistoryService.recordScore(score);
        return new ApiResponse<>(true, "Score recorded for " + username, recorded);
    }

    /**
     * Get score history for a developer (chronological order for charts).
     */
    @GetMapping("/history/{username}")
    public ApiResponse<List<ScoreHistory>> getHistory(@PathVariable String username) {
        List<ScoreHistory> history = scoreHistoryService.getHistoryAscending(username);
        return new ApiResponse<>(true, "Score history for " + username, history);
    }

    /**
     * Get the latest recorded score for a developer.
     */
    @GetMapping("/latest/{username}")
    public ApiResponse<ScoreHistory> getLatest(@PathVariable String username) {
        ScoreHistory latest = scoreHistoryService.getLatest(username);
        if (latest == null) {
            return new ApiResponse<>(false, "No recorded scores for " + username, null);
        }
        return new ApiResponse<>(true, "Latest score for " + username, latest);
    }

    /**
     * Get all recorded history across all developers.
     */
    @GetMapping("/all")
    public ApiResponse<List<ScoreHistory>> getAllHistory() {
        List<ScoreHistory> all = scoreHistoryService.getAllHistory();
        return new ApiResponse<>(true, "All score history", all);
    }

    /**
     * Get report statistics.
     */
    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> getStats() {
        Map<String, Object> stats = scoreHistoryService.getStats();
        return new ApiResponse<>(true, "Report statistics", stats);
    }

    /**
     * Generate and record a score, returning full developer report data.
     */
    @GetMapping("/generate/{username}")
    public ApiResponse<Map<String, Object>> generateReport(@PathVariable String username) {
        List<RepositoryResponse> repos = gitHubService.getRepositories(username);
        GitHubProfileResponse profile = gitHubService.getProfile(username);
        GitHubIntegrationService.EnrichedScoreData enriched = integrationService.getEnrichedScoreData(repos);
        DeveloperScoreResponse score = scoringEngine.calculate(username, repos, profile,
                enriched.weightedLanguages(), enriched.contributors());

        // Record in history
        ScoreHistory recorded = scoreHistoryService.recordScore(score);

        // Get previous scores for trend
        List<ScoreHistory> history = scoreHistoryService.getHistoryAscending(username);

        return new ApiResponse<>(true, "Report generated for " + username,
                Map.of("score", score, "profile", profile, "repos", repos, "history", history, "recorded", recorded));
    }
}
