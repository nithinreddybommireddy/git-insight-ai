package com.gitinsight.githubservice.service;

import com.gitinsight.githubservice.config.GitHubRateLimitInterceptor;
import com.gitinsight.githubservice.dto.response.RepositoryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Enhanced GitHub data integration service.
 * Fetches advanced GitHub data beyond basic profiles and repos.
 */
@Service
public class GitHubIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(GitHubIntegrationService.class);
    private static final String GITHUB_API_BASE = "https://api.github.com";

    /** Caps on per-repo fan-out: the scoring enrichment only needs a sample. */
    private static final int MAX_LANG_REPOS = 15;
    private static final int MAX_CONTRIBUTOR_REPOS = 10;
    private static final int MAX_COMMIT_REPOS = 15;

    /**
     * Per-repo fetches run on virtual threads (Java 21) so N sequential GitHub
     * round-trips collapse to roughly one; the cache itself is thread-safe.
     */
    private static final ExecutorService PARALLEL_FETCHER = Executors.newVirtualThreadPerTaskExecutor();

    private final RestClient restClient;
    private final GitHubCacheService cacheService;

    public record GitHubPR(int number, String title, String state, String createdAt,
                            String mergedAt, String repoName, int comments) {}

    public record GitHubIssue(int number, String title, String state, String createdAt,
                               String closedAt, String repoName, List<String> labels) {}

    public record GitHubCommit(String sha, String message, String date, String repoName) {}

    public record GitHubOrg(String login, String avatarUrl, String description) {}

    /**
     * Contribution statistics. The "recent/sampled" fields are deliberately
     * NOT called totals: they count what GitHub's bounded feeds returned
     * (latest 30 PRs/issues via the search API, PushEvents in the recent
     * 100-event feed), not a developer's lifetime numbers. {@code samplingNote}
     * spells that out for API consumers.
     */
    public record ContributionStats(int recentPushEvents, int sampledPullRequests, int sampledIssues,
                                     int reposContributedTo, int orgCount, String samplingNote) {}

    public record LanguageBreakdown(String language, double percentage, int repos) {}

    public record GitHubEvent(String type, String createdAt, String repoName, String actor) {}

    public record GitHubContributor(String login, int contributions, String avatarUrl) {}

    /**
     * Enriched scoring inputs fetched together: byte-weighted languages and aggregate contributors.
     * Either list may be empty when the underlying API calls fail (graceful fallback in ScoringEngine).
     */
    public record EnrichedScoreData(
            List<LanguageBreakdown> weightedLanguages,
            List<GitHubContributor> contributors
    ) {}

    public record RateLimitResource(int limit, int used, int remaining, long resetEpoch, String resetDate) {}

    public record RateLimitStatus(
            boolean authenticated,
            String hint,
            Map<String, String> headers,
            RateLimitResource core,
            RateLimitResource search,
            RateLimitResource graphql
    ) {}

    public GitHubIntegrationService(
            @Value("${github.token:}") String githubToken,
            GitHubCacheService cacheService,
            GitHubRateLimitInterceptor rateLimitInterceptor) {
        this.cacheService = cacheService;
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(GITHUB_API_BASE)
                .defaultHeader("Accept", "application/vnd.github.v3+json")
                .defaultHeader("User-Agent", "GitInsight-AI/1.0")
                // Explicit timeouts — never rely on the JDK/OS default.
                .requestFactory(com.gitinsight.githubservice.config.HttpClients.githubFactory());

        if (StringUtils.hasText(githubToken)) {
            builder.defaultHeader("Authorization", "Bearer " + githubToken);
        }
        builder.requestInterceptor(rateLimitInterceptor);
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
        int reposChecked = 0;

        for (RepositoryResponse repo : repos) {
            if (count >= 50) break;
            if (reposChecked >= MAX_COMMIT_REPOS) break;
            if (repo.isFork() || repo.isArchived()) continue;
            reposChecked++;

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
        // PushEvents visible in the user's recent 100-event feed. A single
        // PushEvent can contain many commits, and the feed is bounded — this is
        // recent push activity, never "total commits".
        int recentPushEvents = 0;
        try {
            List<Map<String, Object>> events = restClient.get()
                    .uri("/users/{username}/events?per_page=100", username)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
            if (events != null) {
                recentPushEvents = (int) events.stream()
                        .filter(e -> "PushEvent".equals(e.get("type")))
                        .count();
            }
        } catch (Exception e) {
            log.warn("Failed to fetch events for {}: {}", username, e.getMessage());
        }

        int sampledPullRequests = prs != null ? prs.size() : 0;
        int sampledIssues = issues != null ? issues.size() : 0;
        int reposContributed = (int) repos.stream()
                .filter(r -> !r.isFork())
                .count();
        int orgs = getOrganizations(username).size();

        String note = "PRs/issues reflect the latest 30 via the GitHub search API; "
                + "push events reflect the recent 100-event feed. These are samples, not lifetime totals.";
        return new ContributionStats(recentPushEvents, sampledPullRequests, sampledIssues,
                reposContributed, orgs, note);
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

    // ── User Events (own activity) ──

    public List<GitHubEvent> getUserEvents(String username) {
        String cacheKey = "events:" + username;
        List<GitHubEvent> cached = cacheService.get(cacheKey);
        if (cached != null) return cached;

        try {
            List<Map<String, Object>> events = restClient.get()
                    .uri("/users/{username}/events?per_page=100", username)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
            if (events == null) return List.of();

            List<GitHubEvent> result = events.stream()
                    .map(this::toGitHubEvent)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            cacheService.put(cacheKey, result, Duration.ofMinutes(10));
            return result;
        } catch (Exception e) {
            log.warn("Failed to fetch events for {}: {}", username, e.getMessage());
            return List.of();
        }
    }

    // ── Received Events (activity on the user from others) ──

    public List<GitHubEvent> getReceivedEvents(String username) {
        String cacheKey = "events-recv:" + username;
        List<GitHubEvent> cached = cacheService.get(cacheKey);
        if (cached != null) return cached;

        try {
            List<Map<String, Object>> events = restClient.get()
                    .uri("/users/{username}/received_events?per_page=100", username)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
            if (events == null) return List.of();

            List<GitHubEvent> result = events.stream()
                    .map(this::toGitHubEvent)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            cacheService.put(cacheKey, result, Duration.ofMinutes(10));
            return result;
        } catch (Exception e) {
            log.warn("Failed to fetch received events for {}: {}", username, e.getMessage());
            return List.of();
        }
    }

    private GitHubEvent toGitHubEvent(Map<String, Object> e) {
        try {
            String type = (String) e.get("type");
            String createdAt = (String) e.get("created_at");
            String actor = "";
            Object actorObj = e.get("actor");
            if (actorObj instanceof Map<?, ?> actorMap && actorMap.get("login") != null) {
                actor = (String) actorMap.get("login");
            }
            String repoName = "";
            Object repoObj = e.get("repo");
            if (repoObj instanceof Map<?, ?> repoMap && repoMap.get("name") != null) {
                repoName = (String) repoMap.get("name");
            }
            return new GitHubEvent(type, createdAt, repoName, actor);
        } catch (Exception ex) {
            return null;
        }
    }

    // ── Per-repo Languages (byte-weighted) ──

    public Map<String, Long> getRepositoryLanguages(String owner, String repo) {
        String cacheKey = "langs:" + owner + "/" + repo;
        Map<String, Long> cached = cacheService.get(cacheKey);
        if (cached != null) return cached;

        try {
            Map<String, Long> langs = restClient.get()
                    .uri("/repos/{owner}/{repo}/languages", owner, repo)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Long>>() {});
            if (langs == null) return Map.of();

            cacheService.put(cacheKey, langs, Duration.ofHours(1));
            return langs;
        } catch (Exception e) {
            log.warn("Failed to fetch languages for {}/{}: {}", owner, repo, e.getMessage());
            return Map.of();
        }
    }

    // ── Byte-weighted language breakdown across the user's repos ──

    public List<LanguageBreakdown> getWeightedLanguageBreakdown(List<RepositoryResponse> repos) {
        List<RepositoryResponse> effective = repos.stream()
                .filter(r -> !r.isFork())
                .filter(r -> !r.isArchived())
                .sorted(Comparator.comparingInt(RepositoryResponse::getStars).reversed())
                .limit(MAX_LANG_REPOS)
                .collect(Collectors.toList());

        // Fetch all per-repo language payloads in parallel (each is cached for 1h).
        List<CompletableFuture<Map<String, Long>>> futures = effective.stream()
                .map(r -> CompletableFuture.supplyAsync(() -> {
                    String fullName = r.getFullName();
                    if (fullName == null || !fullName.contains("/")) return Map.<String, Long>of();
                    String[] parts = fullName.split("/", 2);
                    return getRepositoryLanguages(parts[0], parts[1]);
                }, PARALLEL_FETCHER))
                .collect(Collectors.toList());

        Map<String, Long> byteTotals = new HashMap<>();
        Map<String, Integer> repoCounts = new HashMap<>();
        for (CompletableFuture<Map<String, Long>> f : futures) {
            Map<String, Long> langs = f.join(); // individual fetches swallow errors already
            langs.forEach((lang, bytes) -> {
                byteTotals.merge(lang, bytes, Long::sum);
                repoCounts.merge(lang, 1, Integer::sum);
            });
        }

        if (byteTotals.isEmpty()) {
            return getLanguageBreakdown(repos); // graceful fallback to repo-count
        }

        long total = byteTotals.values().stream().mapToLong(Long::longValue).sum();
        if (total == 0) return getLanguageBreakdown(repos);

        return byteTotals.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> new LanguageBreakdown(
                        e.getKey(),
                        Math.round((e.getValue() * 100.0 / total) * 10.0) / 10.0,
                        repoCounts.getOrDefault(e.getKey(), 0)))
                .collect(Collectors.toList());
    }

    // ── Per-repo Contributors ──

    public List<GitHubContributor> getContributors(String owner, String repo) {
        String cacheKey = "contrib:" + owner + "/" + repo;
        List<GitHubContributor> cached = cacheService.get(cacheKey);
        if (cached != null) return cached;

        try {
            List<Map<String, Object>> contributors = restClient.get()
                    .uri("/repos/{owner}/{repo}/contributors?per_page=20", owner, repo)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
            if (contributors == null) return List.of();

            List<GitHubContributor> result = contributors.stream()
                    .map(c -> {
                        try {
                            Number contribs = (Number) c.get("contributions");
                            String login = (String) c.get("login");
                            String avatar = (String) c.get("avatar_url");
                            return new GitHubContributor(login, contribs != null ? contribs.intValue() : 0, avatar);
                        } catch (Exception ex) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            cacheService.put(cacheKey, result, Duration.ofHours(1));
            return result;
        } catch (Exception e) {
            log.warn("Failed to fetch contributors for {}/{}: {}", owner, repo, e.getMessage());
            return List.of();
        }
    }

    // ── Aggregate contributors across the user's repos ──

    public List<GitHubContributor> getAggregateContributors(List<RepositoryResponse> repos) {
        List<RepositoryResponse> effective = repos.stream()
                .filter(r -> !r.isFork())
                .filter(r -> !r.isArchived())
                .sorted(Comparator.comparingInt(RepositoryResponse::getStars).reversed())
                .limit(MAX_CONTRIBUTOR_REPOS)
                .collect(Collectors.toList());

        // Fetch all per-repo contributor payloads in parallel (each is cached for 1h).
        List<CompletableFuture<List<GitHubContributor>>> futures = effective.stream()
                .map(r -> CompletableFuture.supplyAsync(() -> {
                    String fullName = r.getFullName();
                    if (fullName == null || !fullName.contains("/")) return List.<GitHubContributor>of();
                    String[] parts = fullName.split("/", 2);
                    return getContributors(parts[0], parts[1]);
                }, PARALLEL_FETCHER))
                .collect(Collectors.toList());

        Map<String, int[]> totals = new HashMap<>(); // login -> [contributions, repoCount]
        for (CompletableFuture<List<GitHubContributor>> f : futures) {
            for (GitHubContributor c : f.join()) {
                if (c.login() == null) continue;
                int[] agg = totals.computeIfAbsent(c.login(), k -> new int[2]);
                agg[0] += c.contributions();
                agg[1] += 1;
            }
        }

        return totals.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue()[0], a.getValue()[0]))
                .limit(20)
                .map(e -> new GitHubContributor(e.getKey(), e.getValue()[0], null))
                .collect(Collectors.toList());
    }

    // ── Per-repo Pull Requests & Issues (core API, not search API) ──

    public List<GitHubPR> getRepositoryPullRequests(String owner, String repo) {
        String cacheKey = "repo-prs:" + owner + "/" + repo;
        List<GitHubPR> cached = cacheService.get(cacheKey);
        if (cached != null) return cached;

        try {
            List<Map<String, Object>> prs = restClient.get()
                    .uri("/repos/{owner}/{repo}/pulls?state=all&per_page=30", owner, repo)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
            if (prs == null) return List.of();

            List<GitHubPR> result = prs.stream()
                    .map(item -> {
                        try {
                            Number num = (Number) item.get("number");
                            Number comments = (Number) item.getOrDefault("comments", 0);
                            return new GitHubPR(
                                    num != null ? num.intValue() : 0,
                                    (String) item.get("title"),
                                    (String) item.get("state"),
                                    (String) item.get("created_at"),
                                    (String) item.get("merged_at"),
                                    owner + "/" + repo,
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
            log.warn("Failed to fetch pulls for {}/{}: {}", owner, repo, e.getMessage());
            return List.of();
        }
    }

    // ── Enriched score inputs (weighted languages + contributors in one call) ──

    public EnrichedScoreData getEnrichedScoreData(List<RepositoryResponse> repos) {
        return new EnrichedScoreData(
                getWeightedLanguageBreakdown(repos),
                getAggregateContributors(repos));
    }

    // ── Rate Limit Status (from x-ratelimit headers + /rate_limit body) ──

    public RateLimitStatus getRateLimit() {
        try {
            ResponseEntity<Map<String, Object>> response = restClient.get()
                    .uri("/rate_limit")
                    .retrieve()
                    .toEntity(new ParameterizedTypeReference<Map<String, Object>>() {});

            // Raw x-ratelimit-* headers from the response
            Map<String, String> headerMap = new LinkedHashMap<>();
            HttpHeaders headers = response.getHeaders();
            for (String name : List.of("X-RateLimit-Limit", "X-RateLimit-Remaining",
                    "X-RateLimit-Used", "X-RateLimit-Reset", "X-RateLimit-Resource")) {
                String value = headers.getFirst(name);
                if (value != null) headerMap.put(name, value);
            }

            // Structured per-resource breakdown from the JSON body
            Map<String, Object> body = response.getBody();
            Map<String, Object> resources = body != null && body.get("resources") instanceof Map<?, ?> m
                    ? toMap(m) : Map.of();

            RateLimitResource core = parseResource(resources.get("core"));
            RateLimitResource search = parseResource(resources.get("search"));
            RateLimitResource graphql = parseResource(resources.get("graphql"));

            boolean authenticated = core.limit() >= 1000;
            String hint = authenticated
                    ? "Authenticated with GITHUB_TOKEN — 5,000 requests/hour."
                    : "Unauthenticated — only 60 requests/hour. Set GITHUB_TOKEN to raise the limit to 5,000.";

            return new RateLimitStatus(authenticated, hint, headerMap, core, search, graphql);
        } catch (Exception e) {
            log.warn("Failed to fetch rate limit status: {}", e.getMessage());
            RateLimitResource empty = new RateLimitResource(0, 0, 0, 0, "");
            return new RateLimitStatus(false,
                    "Could not fetch rate limit status: " + e.getMessage(),
                    Map.of(), empty, empty, empty);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Map<?, ?> m) {
        return (Map<String, Object>) m;
    }

    private RateLimitResource parseResource(Object o) {
        if (!(o instanceof Map<?, ?> m)) return new RateLimitResource(0, 0, 0, 0, "");
        Number limit = num(m.get("limit"));
        Number used = num(m.get("used"));
        Number remaining = num(m.get("remaining"));
        Number reset = num(m.get("reset"));
        long resetEpoch = reset != null ? reset.longValue() : 0;
        String resetDate = resetEpoch > 0 ? Instant.ofEpochSecond(resetEpoch).toString() : "";
        return new RateLimitResource(
                limit != null ? limit.intValue() : 0,
                used != null ? used.intValue() : 0,
                remaining != null ? remaining.intValue() : 0,
                resetEpoch,
                resetDate);
    }

    private Number num(Object o) {
        return o instanceof Number n ? n : null;
    }

    public List<GitHubIssue> getRepositoryIssues(String owner, String repo) {
        String cacheKey = "repo-issues:" + owner + "/" + repo;
        List<GitHubIssue> cached = cacheService.get(cacheKey);
        if (cached != null) return cached;

        try {
            List<Map<String, Object>> issues = restClient.get()
                    .uri("/repos/{owner}/{repo}/issues?state=all&per_page=30", owner, repo)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
            if (issues == null) return List.of();

            List<GitHubIssue> result = issues.stream()
                    .filter(item -> !Boolean.TRUE.equals(item.get("pull_request")))
                    .map(item -> {
                        try {
                            @SuppressWarnings("unchecked")
                            List<Map<String, Object>> labelList =
                                    (List<Map<String, Object>>) item.getOrDefault("labels", List.of());
                            List<String> labels = labelList.stream()
                                    .map(l -> (String) l.get("name"))
                                    .collect(Collectors.toList());
                            Number num = (Number) item.get("number");
                            return new GitHubIssue(
                                    num != null ? num.intValue() : 0,
                                    (String) item.get("title"),
                                    (String) item.get("state"),
                                    (String) item.get("created_at"),
                                    (String) item.get("closed_at"),
                                    owner + "/" + repo,
                                    labels
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
            log.warn("Failed to fetch issues for {}/{}: {}", owner, repo, e.getMessage());
            return List.of();
        }
    }
}
