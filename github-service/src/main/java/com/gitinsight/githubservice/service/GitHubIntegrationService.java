package com.gitinsight.githubservice.service;

import com.gitinsight.githubservice.dto.response.RepositoryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Enhanced GitHub data integration service.
 * Fetches advanced GitHub data beyond basic profiles and repos.
 */
@Service
public class GitHubIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(GitHubIntegrationService.class);
    private static final String GITHUB_API_BASE = "https://api.github.com";

    private final RestClient restClient;
    private final GitHubCacheService cacheService;

    public record GitHubPR(int number, String title, String state, String createdAt,
                            String mergedAt, String repoName, int comments) {}

    public record GitHubIssue(int number, String title, String state, String createdAt,
                               String closedAt, String repoName, List<String> labels) {}

    public record GitHubCommit(String sha, String message, String date, String repoName) {}

    public record GitHubOrg(String login, String avatarUrl, String description) {}

    public record ContributionStats(int totalCommits, int totalPRs, int totalIssues,
                                     int reposContributedTo, int orgCount) {}

    public record LanguageBreakdown(String language, double percentage, int repos) {}

    public GitHubIntegrationService(
            @Value("${github.token:}") String githubToken,
            GitHubCacheService cacheService) {
        this.cacheService = cacheService;
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(GITHUB_API_BASE)
                .defaultHeader("Accept", "application/vnd.github.v3+json")
                .defaultHeader("User-Agent", "GitInsight-AI/1.0");

        if (StringUtils.hasText(githubToken)) {
            builder.defaultHeader("Authorization", "Bearer " + githubToken);
        }
        this.restClient = builder.build();
    }

    // ── Organizations ──

    public List<GitHubOrg> getOrganizations(String username) {
        String cacheKey = "orgs:" + username;
        List<GitHubOrg> cached = cacheService.get(cacheKey);
        if (cached != null) return cached;

        try {
            List<Map<String, Object>> orgs = restClient.get()
                    .uri("/users/{username}/orgs", username)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});

            if (orgs == null) return List.of();

            List<GitHubOrg> result = orgs.stream()
                    .map(o -> new GitHubOrg(
                            (String) o.get("login"),
                            (String) o.get("avatar_url"),
                            (String) o.get("description")))
                    .collect(Collectors.toList());

            cacheService.put(cacheKey, result, Duration.ofHours(1));
            return result;

        } catch (Exception e) {
            log.warn("Failed to fetch orgs for {}: {}", username, e.getMessage());
            return List.of();
        }
    }

    // ── Pull Requests (latest 30 across all repos) ──

    public List<GitHubPR> getPullRequests(String username) {
        String cacheKey = "prs:" + username;
        List<GitHubPR> cached = cacheService.get(cacheKey);
        if (cached != null) return cached;

        try {
            Map<String, Object> searchResult = restClient.get()
                    .uri("/search/issues?q=author:{username}+type:pr&sort=created&order=desc&per_page=30",
                            username)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});

            if (searchResult == null || searchResult.get("items") == null) return List.of();

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) searchResult.get("items");
            List<GitHubPR> result = items.stream()
                    .map(item -> {
                        try {
                            String repoUrl = (String) item.get("repository_url");
                            String repoName = repoUrl != null
                                    ? repoUrl.replace("https://api.github.com/repos/", "")
                                    : "";
                            Number num = (Number) item.get("number");
                            Number comments = (Number) item.getOrDefault("comments", 0);
                            return new GitHubPR(
                                    num != null ? num.intValue() : 0,
                                    (String) item.get("title"),
                                    (String) item.get("state"),
                                    (String) item.get("created_at"),
                                    (String) item.get("merged_at"),
                                    repoName,
                                    comments != null ? comments.intValue() : 0
                            );
                        } catch (Exception e) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            cacheService.put(cacheKey, result, Duration.ofMinutes(10));
            return result;

        } catch (Exception e) {
            log.warn("Failed to fetch PRs for {}: {}", username, e.getMessage());
            return List.of();
        }
    }

    // ── Issues (latest 30) ──

    public List<GitHubIssue> getIssues(String username) {
        String cacheKey = "issues:" + username;
        List<GitHubIssue> cached = cacheService.get(cacheKey);
        if (cached != null) return cached;

        try {
            Map<String, Object> searchResult = restClient.get()
                    .uri("/search/issues?q=author:{username}+type:issue&sort=created&order=desc&per_page=30",
                            username)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});

            if (searchResult == null || searchResult.get("items") == null) return List.of();

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) searchResult.get("items");
            List<GitHubIssue> issues = items.stream()
                    .map(item -> {
                        try {
                            @SuppressWarnings("unchecked")
                            List<Map<String, Object>> labelList =
                                    (List<Map<String, Object>>) item.getOrDefault("labels", List.of());
                            List<String> labels = labelList.stream()
                                    .map(l -> (String) l.get("name"))
                                    .collect(Collectors.toList());
                            String repoUrl = (String) item.get("repository_url");
                            String repoName = repoUrl != null
                                    ? repoUrl.replace("https://api.github.com/repos/", "")
                                    : "";
                            Number num = (Number) item.get("number");
                            return new GitHubIssue(
                                    num != null ? num.intValue() : 0,
                                    (String) item.get("title"),
                                    (String) item.get("state"),
                                    (String) item.get("created_at"),
                                    (String) item.get("closed_at"),
                                    repoName,
                                    labels
                            );
                        } catch (Exception e) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            cacheService.put(cacheKey, issues, Duration.ofMinutes(10));
            return issues;

        } catch (Exception e) {
            log.warn("Failed to fetch issues for {}: {}", username, e.getMessage());
            return List.of();
        }
    }

    // ── Recent Commits ──

    public List<GitHubCommit> getRecentCommits(String username, List<RepositoryResponse> repos) {
        String cacheKey = "commits:" + username;
        List<GitHubCommit> cached = cacheService.get(cacheKey);
        if (cached != null) return cached;

        List<GitHubCommit> allCommits = new ArrayList<>();
        int count = 0;

        for (RepositoryResponse repo : repos) {
            if (count >= 50) break;
            if (repo.isFork() || repo.isArchived()) continue;

            try {
                List<Map<String, Object>> commits = restClient.get()
                        .uri("/repos/{owner}/{repo}/commits?author={username}&per_page=5",
                                username, repo.getName(), username)
                        .retrieve()
                        .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});

                if (commits != null) {
                    for (Map<String, Object> c : commits) {
                        if (count >= 50) break;
                        @SuppressWarnings("unchecked")
                        Map<String, Object> commitInfo = (Map<String, Object>) c.get("commit");
                        String message = commitInfo != null ? (String) commitInfo.get("message") : "";
                        String date = commitInfo != null && commitInfo.get("author") != null
                                ? (String) ((Map<?, ?>) commitInfo.get("author")).get("date")
                                : "";
                        allCommits.add(new GitHubCommit(
                                (String) c.get("sha"),
                                message != null ? message.lines().findFirst().orElse("") : "",
                                date,
                                repo.getName()
                        ));
                        count++;
                    }
                }
            } catch (Exception e) {
                // Skip repos that fail
            }
        }

        cacheService.put(cacheKey, allCommits, Duration.ofMinutes(10));
        return allCommits;
    }

    // ── Contribution Stats ──

    public ContributionStats getContributionStats(String username, List<RepositoryResponse> repos,
                                                   List<GitHubPR> prs, List<GitHubIssue> issues) {
        int totalCommits = 0;
        try {
            List<Map<String, Object>> events = restClient.get()
                    .uri("/users/{username}/events?per_page=100", username)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
            if (events != null) {
                totalCommits = (int) events.stream()
                        .filter(e -> "PushEvent".equals(e.get("type")))
                        .count();
            }
        } catch (Exception e) {
            log.warn("Failed to fetch events for {}: {}", username, e.getMessage());
        }

        int totalPRs = prs != null ? prs.size() : 0;
        int totalIssues = issues != null ? issues.size() : 0;
        int reposContributed = (int) repos.stream()
                .filter(r -> !r.isFork())
                .count();
        int orgs = getOrganizations(username).size();

        return new ContributionStats(totalCommits, totalPRs, totalIssues, reposContributed, orgs);
    }

    // ── Language Breakdown ──

    public List<LanguageBreakdown> getLanguageBreakdown(List<RepositoryResponse> repos) {
        Map<String, Integer> langCount = new HashMap<>();
        for (RepositoryResponse r : repos) {
            if (r.getLanguage() != null && !r.getLanguage().isEmpty()) {
                langCount.merge(r.getLanguage(), 1, Integer::sum);
            }
        }

        if (langCount.isEmpty()) return List.of();

        int total = langCount.values().stream().mapToInt(Integer::intValue).sum();
        return langCount.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .map(e -> new LanguageBreakdown(
                        e.getKey(),
                        Math.round((e.getValue() * 100.0 / total) * 10.0) / 10.0,
                        e.getValue()))
                .collect(Collectors.toList());
    }
}
