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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Report endpoints (require a valid auth-service JWT).
 *
 * <p>Ownership model: every snapshot is attributed to the authenticated user
 * who saved it. A USER can only read/record their own saved reports; a
 * RECRUITER or ADMIN sees all reports (recruiter flows operate on candidate
 * reports). A user can still record a snapshot for any public GitHub username
 * — that is the product — but it lands in their own history, so nobody can
 * pollute or mine another user's saved data.
 */
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

    /** The requesting user's id and whether they see all reports (RECRUITER/ADMIN). */
    private record ReportScope(Long ownerId, boolean privileged) {}

    private ReportScope scope(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        boolean privileged = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> "ROLE_RECRUITER".equals(a) || "ROLE_ADMIN".equals(a));
        return new ReportScope(userId, privileged);
    }

    /**
     * Generate and record a new score snapshot for the given user, owned by the caller.
     */
    @PostMapping("/record/{username}")
    public ApiResponse<ScoreHistory> recordScore(@PathVariable String username,
                                                 Authentication authentication) {
        DeveloperScoreResponse score = computeScore(username);
        ScoreHistory recorded = scoreHistoryService.recordScore(score, scope(authentication).ownerId());
        return new ApiResponse<>(true, "Score recorded for " + username, recorded);
    }

    /**
     * Get score history for a developer (chronological order for charts).
     * Regular users see only the snapshots they recorded for this username.
     */
    @GetMapping("/history/{username}")
    public ApiResponse<List<ScoreHistory>> getHistory(@PathVariable String username,
                                                      Authentication authentication) {
        ReportScope s = scope(authentication);
        List<ScoreHistory> history = scoreHistoryService.getHistoryAscending(username, s.ownerId(), s.privileged());
        return new ApiResponse<>(true, "Score history for " + username, history);
    }

    /**
     * Get the latest recorded score for a developer (scoped like {@code /history}).
     */
    @GetMapping("/latest/{username}")
    public ApiResponse<ScoreHistory> getLatest(@PathVariable String username,
                                               Authentication authentication) {
        ReportScope s = scope(authentication);
        ScoreHistory latest = scoreHistoryService.getLatest(username, s.ownerId(), s.privileged());
        if (latest == null) {
            return new ApiResponse<>(false, "No recorded scores for " + username, null);
        }
        return new ApiResponse<>(true, "Latest score for " + username, latest);
    }

    /**
     * Get recorded history across developers: own snapshots for USER, all for RECRUITER/ADMIN.
     */
    @GetMapping("/all")
    public ApiResponse<List<ScoreHistory>> getAllHistory(Authentication authentication) {
        ReportScope s = scope(authentication);
        List<ScoreHistory> all = scoreHistoryService.getAllHistory(s.ownerId(), s.privileged());
        return new ApiResponse<>(true, "Score history", all);
    }

    /**
     * Report statistics over the caller's visible snapshots (own for USER, global for RECRUITER/ADMIN).
     */
    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> getStats(Authentication authentication) {
        ReportScope s = scope(authentication);
        Map<String, Object> stats = scoreHistoryService.getStats(s.ownerId(), s.privileged());
        return new ApiResponse<>(true, "Report statistics", stats);
    }

    /**
     * Generate and record a score, returning full developer report data.
     */
    @GetMapping("/generate/{username}")
    public ApiResponse<Map<String, Object>> generateReport(@PathVariable String username,
                                                           Authentication authentication) {
        ReportScope s = scope(authentication);
        DeveloperScoreResponse score = computeScore(username);

        // Record in history (owned by the caller)
        ScoreHistory recorded = scoreHistoryService.recordScore(score, s.ownerId());

        // Previous scores for trend (scoped like /history)
        List<ScoreHistory> history = scoreHistoryService.getHistoryAscending(username, s.ownerId(), s.privileged());

        GitHubProfileResponse profile = gitHubService.getProfile(username);
        List<RepositoryResponse> repos = gitHubService.getRepositories(username);

        return new ApiResponse<>(true, "Report generated for " + username,
                Map.of("score", score, "profile", profile, "repos", repos, "history", history, "recorded", recorded));
    }

    private DeveloperScoreResponse computeScore(String username) {
        List<RepositoryResponse> repos = gitHubService.getRepositories(username);
        GitHubProfileResponse profile = gitHubService.getProfile(username);
        GitHubIntegrationService.EnrichedScoreData enriched = integrationService.getEnrichedScoreData(repos);
        List<GitHubIntegrationService.GitHubCommit> recentCommits =
                integrationService.getRecentCommits(username, repos);
        return scoringEngine.calculate(username, repos, profile,
                enriched.weightedLanguages(), enriched.contributors(), recentCommits);
    }
}
