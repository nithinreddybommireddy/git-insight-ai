package com.gitinsight.githubservice.service;

import com.gitinsight.githubservice.dto.response.DeveloperScoreResponse;
import com.gitinsight.githubservice.dto.response.GitHubProfileResponse;
import com.gitinsight.githubservice.dto.response.RepositoryResponse;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * Performance layer for the developer score.
 * <p>
 * Every score/AI/compare/report read used to recompute the score from scratch
 * (repos + profile + per-repo languages + per-repo contributors → 10-metric
 * engine), fanning out to dozens of GitHub API calls per request. This service
 * caches the fully computed {@link DeveloperScoreResponse} per username so
 * repeat views are one cache hit and zero GitHub calls. Sub-parts (profile,
 * repos, per-repo languages/contributors) are cached separately, so even a
 * cache miss here is cheap.
 */
@Service
public class DeveloperScoreService {

    private static final Duration SCORE_TTL = Duration.ofMinutes(30);

    private final GitHubService gitHubService;
    private final GitHubIntegrationService integrationService;
    private final ScoringEngine scoringEngine;
    private final GitHubCacheService cacheService;

    public DeveloperScoreService(GitHubService gitHubService,
                                 GitHubIntegrationService integrationService,
                                 ScoringEngine scoringEngine,
                                 GitHubCacheService cacheService) {
        this.gitHubService = gitHubService;
        this.integrationService = integrationService;
        this.scoringEngine = scoringEngine;
        this.cacheService = cacheService;
    }

    /**
     * Full developer score (10 metrics + insights + breakdowns), cached 30 min.
     */
    public DeveloperScoreResponse getScore(String username) {
        String cacheKey = "score:" + username;
        DeveloperScoreResponse cached = cacheService.get(cacheKey);
        if (cached != null) return cached;

        List<RepositoryResponse> repos = gitHubService.getRepositories(username);
        GitHubProfileResponse profile = gitHubService.getProfile(username);
        GitHubIntegrationService.EnrichedScoreData enriched = integrationService.getEnrichedScoreData(repos);
        // The developer's own recent commits drive the developer-level metrics
        // (recency/frequency) — never another committer's pushes.
        List<GitHubIntegrationService.GitHubCommit> recentCommits =
                integrationService.getRecentCommits(username, repos);
        DeveloperScoreResponse score = scoringEngine.calculate(username, repos, profile,
                enriched.weightedLanguages(), enriched.contributors(), recentCommits);

        cacheService.put(cacheKey, score, SCORE_TTL);
        return score;
    }

    /**
     * Force a fresh computation (used by flows that must record an up-to-date
     * snapshot, e.g. report generation). Still benefits from the 5-min profile /
     * repos cache and the per-repo enrichment cache.
     */
    public DeveloperScoreResponse getScoreFresh(String username) {
        List<RepositoryResponse> repos = gitHubService.getRepositories(username);
        GitHubProfileResponse profile = gitHubService.getProfile(username);
        GitHubIntegrationService.EnrichedScoreData enriched = integrationService.getEnrichedScoreData(repos);
        List<GitHubIntegrationService.GitHubCommit> recentCommits =
                integrationService.getRecentCommits(username, repos);
        return scoringEngine.calculate(username, repos, profile,
                enriched.weightedLanguages(), enriched.contributors(), recentCommits);
    }
}
