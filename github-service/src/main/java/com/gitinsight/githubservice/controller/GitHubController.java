package com.gitinsight.githubservice.controller;

import com.gitinsight.common.dto.response.ApiResponse;
import com.gitinsight.githubservice.dto.response.*;
import com.gitinsight.githubservice.service.GitHubIntegrationService;
import com.gitinsight.githubservice.service.GitHubIntegrationService.*;
import com.gitinsight.githubservice.service.GitHubService;
import com.gitinsight.githubservice.service.ScoringEngine;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/github")
public class GitHubController {

    private final GitHubService gitHubService;
    private final ScoringEngine scoringEngine;
    private final GitHubIntegrationService integrationService;

    public GitHubController(GitHubService gitHubService, ScoringEngine scoringEngine,
                            GitHubIntegrationService integrationService) {
        this.gitHubService = gitHubService;
        this.scoringEngine = scoringEngine;
        this.integrationService = integrationService;
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
        DeveloperScoreResponse score = scoringEngine.calculate(username, repos, profile);
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

    @GetMapping("/{username}/languages")
    public ApiResponse<List<LanguageBreakdown>> getLanguageBreakdown(@PathVariable String username) {
        List<RepositoryResponse> repos = gitHubService.getRepositories(username);
        List<LanguageBreakdown> langs = integrationService.getLanguageBreakdown(repos);
        return new ApiResponse<>(true, "Language breakdown fetched successfully.", langs);
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
        DeveloperScoreResponse score = scoringEngine.calculate(username, repos, profile);
        return new ApiResponse<>(true, "Full developer insights calculated.", score);
    }
}
