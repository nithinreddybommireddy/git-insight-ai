package com.gitinsight.githubservice.controller;

import com.gitinsight.common.dto.response.ApiResponse;
import com.gitinsight.githubservice.dto.response.*;
import com.gitinsight.githubservice.service.CommitQualityService;
import com.gitinsight.githubservice.service.GitHubIntegrationService;
import com.gitinsight.githubservice.service.GitHubIntegrationService.*;
import com.gitinsight.githubservice.service.GitHubService;
import com.gitinsight.githubservice.service.ScoringEngine;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/github")
public class GitHubController {

    private final GitHubService gitHubService;
    private final ScoringEngine scoringEngine;
    private final GitHubIntegrationService integrationService;
    private final CommitQualityService commitQualityService;

    public GitHubController(GitHubService gitHubService, ScoringEngine scoringEngine,
                            GitHubIntegrationService integrationService,
                            CommitQualityService commitQualityService) {
        this.gitHubService = gitHubService;
        this.scoringEngine = scoringEngine;
        this.integrationService = integrationService;
        this.commitQualityService = commitQualityService;
    }

    @GetMapping("/profile/{username}")
    public ApiResponse<GitHubProfileResponse> getProfile(@PathVariable String username) {
        GitHubProfileResponse profile = gitHubService.getProfile(username);
        return new ApiResponse<>(true, "GitHub profile fetched successfully.", profile);
    }

    @GetMapping("/{username}/repos")
    public ApiResponse<List<RepositoryResponse>> getRepositories(@PathVariable String username) {
        List<RepositoryResponse> repos = gitHubService.getRepositories(username);
        return new ApiResponse<>(true, "Repositories fetched successfully.", repos);
    }

    @GetMapping("/{username}/score")
    public ApiResponse<DeveloperScoreResponse> getDeveloperScore(@PathVariable String username) {
        List<RepositoryResponse> repos = gitHubService.getRepositories(username);
        GitHubProfileResponse profile = gitHubService.getProfile(username);
        GitHubIntegrationService.EnrichedScoreData enriched = integrationService.getEnrichedScoreData(repos);
        DeveloperScoreResponse score = scoringEngine.calculate(username, repos, profile,
                enriched.weightedLanguages(), enriched.contributors());
        return new ApiResponse<>(true, "Developer score calculated successfully.", score);
    }

    // ── Enhanced Integration Endpoints ──

    @GetMapping("/{username}/organizations")
    public ApiResponse<List<GitHubOrg>> getOrganizations(@PathVariable String username) {
        List<GitHubOrg> orgs = integrationService.getOrganizations(username);
        return new ApiResponse<>(true, "Organizations fetched successfully.", orgs);
    }

    @GetMapping("/{username}/pull-requests")
    public ApiResponse<List<GitHubPR>> getPullRequests(@PathVariable String username) {
        List<GitHubPR> prs = integrationService.getPullRequests(username);
        return new ApiResponse<>(true, "Pull requests fetched successfully.", prs);
    }

    @GetMapping("/{username}/issues")
    public ApiResponse<List<GitHubIssue>> getIssues(@PathVariable String username) {
        List<GitHubIssue> issues = integrationService.getIssues(username);
        return new ApiResponse<>(true, "Issues fetched successfully.", issues);
    }

    @GetMapping("/{username}/commits")
    public ApiResponse<List<GitHubCommit>> getCommits(@PathVariable String username) {
        List<RepositoryResponse> repos = gitHubService.getRepositories(username);
        List<GitHubCommit> commits = integrationService.getRecentCommits(username, repos);
        return new ApiResponse<>(true, "Commits fetched successfully.", commits);
    }

    /**
     * Phase 5 — Commit & Code Quality Analysis.
     */
    @GetMapping("/{username}/commits/analytics")
    public ApiResponse<CommitAnalyticsResponse> getCommitAnalytics(@PathVariable String username) {
        List<RepositoryResponse> repos = gitHubService.getRepositories(username);
        CommitAnalyticsResponse analytics = commitQualityService.analyze(username, repos);
        return new ApiResponse<>(true, "Commit & code quality analytics calculated successfully.", analytics);
    }

    @GetMapping("/{username}/languages")
    public ApiResponse<List<LanguageBreakdown>> getLanguageBreakdown(@PathVariable String username) {
        List<RepositoryResponse> repos = gitHubService.getRepositories(username);
        List<LanguageBreakdown> langs = integrationService.getLanguageBreakdown(repos);
        return new ApiResponse<>(true, "Language breakdown fetched successfully.", langs);
    }

    @GetMapping("/rate-limit")
    public ApiResponse<RateLimitStatus> getRateLimit() {
        RateLimitStatus status = integrationService.getRateLimit();
        return new ApiResponse<>(true, "GitHub rate limit status fetched successfully.", status);
    }

    @GetMapping("/{username}/events")
    public ApiResponse<List<GitHubEvent>> getUserEvents(@PathVariable String username) {
        List<GitHubEvent> events = integrationService.getUserEvents(username);
        return new ApiResponse<>(true, "User events fetched successfully.", events);
    }

    @GetMapping("/{username}/received-events")
    public ApiResponse<List<GitHubEvent>> getReceivedEvents(@PathVariable String username) {
        List<GitHubEvent> events = integrationService.getReceivedEvents(username);
        return new ApiResponse<>(true, "Received events fetched successfully.", events);
    }

    @GetMapping("/{username}/languages/weighted")
    public ApiResponse<List<LanguageBreakdown>> getWeightedLanguages(@PathVariable String username) {
        List<RepositoryResponse> repos = gitHubService.getRepositories(username);
        List<LanguageBreakdown> langs = integrationService.getWeightedLanguageBreakdown(repos);
        return new ApiResponse<>(true, "Byte-weighted language breakdown fetched successfully.", langs);
    }

    @GetMapping("/{username}/contributors")
    public ApiResponse<List<GitHubContributor>> getAggregateContributors(@PathVariable String username) {
        List<RepositoryResponse> repos = gitHubService.getRepositories(username);
        List<GitHubContributor> contributors = integrationService.getAggregateContributors(repos);
        return new ApiResponse<>(true, "Aggregate contributors fetched successfully.", contributors);
    }

    // ── Per-repo endpoints (owner/repo path) ──

    @GetMapping("/{owner}/{repo}/languages")
    public ApiResponse<Map<String, Long>> getRepositoryLanguages(@PathVariable String owner, @PathVariable String repo) {
        Map<String, Long> langs = integrationService.getRepositoryLanguages(owner, repo);
        return new ApiResponse<>(true, "Repository languages fetched successfully.", langs);
    }

    @GetMapping("/{owner}/{repo}/contributors")
    public ApiResponse<List<GitHubContributor>> getRepositoryContributors(@PathVariable String owner, @PathVariable String repo) {
        List<GitHubContributor> contributors = integrationService.getContributors(owner, repo);
        return new ApiResponse<>(true, "Repository contributors fetched successfully.", contributors);
    }

    @GetMapping("/{owner}/{repo}/pulls")
    public ApiResponse<List<GitHubPR>> getRepositoryPullRequests(@PathVariable String owner, @PathVariable String repo) {
        List<GitHubPR> prs = integrationService.getRepositoryPullRequests(owner, repo);
        return new ApiResponse<>(true, "Repository pull requests fetched successfully.", prs);
    }

    @GetMapping("/{owner}/{repo}/issues")
    public ApiResponse<List<GitHubIssue>> getRepositoryIssues(@PathVariable String owner, @PathVariable String repo) {
        List<GitHubIssue> issues = integrationService.getRepositoryIssues(owner, repo);
        return new ApiResponse<>(true, "Repository issues fetched successfully.", issues);
    }

    @GetMapping("/{username}/contribution-stats")
    public ApiResponse<ContributionStats> getContributionStats(@PathVariable String username) {
        List<RepositoryResponse> repos = gitHubService.getRepositories(username);
        List<GitHubPR> prs = integrationService.getPullRequests(username);
        List<GitHubIssue> issues = integrationService.getIssues(username);
        ContributionStats stats = integrationService.getContributionStats(username, repos, prs, issues);
        return new ApiResponse<>(true, "Contribution stats fetched successfully.", stats);
    }

    @GetMapping("/{username}/insights")
    public ApiResponse<DeveloperScoreResponse> getFullInsights(@PathVariable String username) {
        List<RepositoryResponse> repos = gitHubService.getRepositories(username);
        GitHubProfileResponse profile = gitHubService.getProfile(username);
        GitHubIntegrationService.EnrichedScoreData enriched = integrationService.getEnrichedScoreData(repos);
        DeveloperScoreResponse score = scoringEngine.calculate(username, repos, profile,
                enriched.weightedLanguages(), enriched.contributors());
        return new ApiResponse<>(true, "Full developer insights calculated.", score);
    }
}
