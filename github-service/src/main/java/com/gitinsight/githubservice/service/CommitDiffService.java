package com.gitinsight.githubservice.service;

import com.gitinsight.githubservice.dto.response.CommitDiffListResponse;
import com.gitinsight.githubservice.dto.response.CommitDiffResponse;
import com.gitinsight.githubservice.dto.response.RepositoryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Phase 6 — Commit-diff AI code-quality review support.
 * <p>
 * Fetches the real per-file diffs (patches) of a developer's recent commits
 * from the GitHub API. Diff fetching is intentionally capped (repos, commits,
 * files per commit, patch size) so one request stays cheap and rate-limit
 * friendly. The returned diffs feed the AI per-file review in
 * {@code GeminiService#generateCommitDiffReview}.
 */
@Service
public class CommitDiffService {

    private static final Logger log = LoggerFactory.getLogger(CommitDiffService.class);
    private static final String GITHUB_API_BASE = "https://api.github.com";

    private static final int MAX_REPOS = 8;              // cap API calls per request
    private static final int COMMITS_PER_REPO = 10;
    private static final int MAX_TOTAL_COMMITS = 15;
    private static final int MAX_FILES_PER_COMMIT = 12;
    private static final int MAX_PATCH_CHARS = 6000;     // truncate huge patches

    private final RestClient restClient;
    private final GitHubCacheService cacheService;

    @Autowired
    public CommitDiffService(
            @Value("${github.token:}") String githubToken,
            GitHubCacheService cacheService) {
        this(buildRestClient(githubToken), cacheService);
    }

    /**
     * Package-private constructor for unit tests — allows injecting a mock RestClient.
     */
    CommitDiffService(RestClient restClient, GitHubCacheService cacheService) {
        this.restClient = restClient;
        this.cacheService = cacheService;
    }

    private static RestClient buildRestClient(String githubToken) {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(GITHUB_API_BASE)
                .defaultHeader("Accept", "application/vnd.github.v3+json")
                .defaultHeader("User-Agent", "GitInsight-AI/1.0")
                // Explicit timeouts — never rely on the JDK/OS default.
                .requestFactory(com.gitinsight.githubservice.config.HttpClients.githubFactory());

        if (StringUtils.hasText(githubToken)) {
            builder.defaultHeader("Authorization", "Bearer " + githubToken);
        }
        return builder.build();
    }

    // ═══════════════════════════════════════════════════════════════
    // MAIN ENTRY POINT
    // ═══════════════════════════════════════════════════════════════

    public CommitDiffListResponse getRecentDiffs(String username, List<RepositoryResponse> repos, int limit) {
        int target = Math.max(1, Math.min(limit, 30));
        String cacheKey = "commit-diffs:" + username + ":" + target;
        CommitDiffListResponse cached = cacheService.get(cacheKey);
        if (cached != null) return cached;

        if (repos == null || repos.isEmpty()) {
            return CommitDiffListResponse.empty(username);
        }

        // Same filtering rules as ScoringEngine / CommitQualityService:
        // only original, non-archived repositories.
        List<RepositoryResponse> effective = repos.stream()
                .filter(r -> !r.isFork())
                .filter(r -> !r.isArchived())
                .collect(Collectors.toList());
        if (effective.isEmpty()) {
            return CommitDiffListResponse.empty(username);
        }

        List<CommitDiffResponse> commits = new ArrayList<>();
        int reposProcessed = 0;

        for (RepositoryResponse repo : effective) {
            if (reposProcessed >= MAX_REPOS || commits.size() >= target) break;
            reposProcessed++;
            commits.addAll(fetchRepoDiffs(username, repo, target - commits.size()));
        }

        CommitDiffListResponse response = new CommitDiffListResponse();
        response.setUsername(username);
        response.setTotalCommits(commits.size());
        response.setCommits(commits);
        cacheService.put(cacheKey, response, Duration.ofMinutes(10));
        return response;
    }

    // ═══════════════════════════════════════════════════════════════
    // GITHUB FETCH
    // ═══════════════════════════════════════════════════════════════

    private List<CommitDiffResponse> fetchRepoDiffs(String username, RepositoryResponse repo, int remaining) {
        String fullName = repo.getFullName();
        if (fullName == null || !fullName.contains("/")) return List.of();
        String[] parts = fullName.split("/", 2);
        List<CommitDiffResponse> out = new ArrayList<>();

        try {
            List<Map<String, Object>> commits = restClient.get()
                    .uri("/repos/{owner}/{repo}/commits?author={author}&per_page={perPage}",
                            parts[0], parts[1], username, COMMITS_PER_REPO)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});

            if (commits == null) return List.of();

            for (Map<String, Object> c : commits) {
                if (out.size() >= remaining) break;
                String sha = c.get("sha") instanceof String s ? s : null;
                if (!StringUtils.hasText(sha)) continue;
                try {
                    CommitDiffResponse diff = fetchCommitDetail(parts[0], parts[1], repo.getName(), sha);
                    if (diff != null) out.add(diff);
                } catch (Exception e) {
                    log.warn("Failed to fetch diff for {}/{}@{}: {}",
                            parts[0], parts[1], sha, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to list commits for {}/{}: {}", parts[0], parts[1], e.getMessage());
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private CommitDiffResponse fetchCommitDetail(String owner, String repo, String repoName, String sha) {
        Map<String, Object> detail = restClient.get()
                .uri("/repos/{owner}/{repo}/commits/{sha}", owner, repo, sha)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});

        if (detail == null) return null;

        CommitDiffResponse diff = new CommitDiffResponse();
        diff.setSha(sha);
        diff.setRepoName(repoName);

        Map<String, Object> commitInfo = detail.get("commit") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : null;
        if (commitInfo != null) {
            if (commitInfo.get("message") instanceof String msg) diff.setMessage(msg);
            if (commitInfo.get("author") instanceof Map<?, ?> a && a.get("date") instanceof String d) {
                diff.setDate(d);
            }
        }

        Object filesObj = detail.get("files");
        int added = 0, deleted = 0, count = 0;
        if (filesObj instanceof List<?> files) {
            for (Object f : files) {
                if (count >= MAX_FILES_PER_COMMIT) break;
                if (!(f instanceof Map<?, ?> raw)) continue;
                try {
                    Map<String, Object> file = (Map<String, Object>) raw;
                    CommitDiffResponse.FileDiff fd = new CommitDiffResponse.FileDiff();
                    fd.setFilename(file.get("filename") instanceof String s ? s : "");
                    fd.setStatus(file.get("status") instanceof String s ? s : "modified");
                    fd.setPreviousFilename(file.get("previous_filename") instanceof String s ? s : null);
                    fd.setAdditions(num(file.get("additions")));
                    fd.setDeletions(num(file.get("deletions")));
                    fd.setChanges(num(file.get("changes")));
                    fd.setPatch(file.get("patch") instanceof String p ? truncatePatch(p) : null);
                    added += fd.getAdditions();
                    deleted += fd.getDeletions();
                    diff.getFiles().add(fd);
                    count++;
                } catch (Exception ignored) {
                    // skip malformed file entry
                }
            }
        }

        diff.setAdditions(added);
        diff.setDeletions(deleted);
        diff.setChangedFiles(count);

        if (diff.getFiles().isEmpty()) return null; // merge/empty commits add no review value
        return diff;
    }

    private static int num(Object o) {
        return o instanceof Number n ? n.intValue() : 0;
    }

    static String truncatePatch(String patch) {
        if (patch == null || patch.length() <= MAX_PATCH_CHARS) return patch;
        return patch.substring(0, MAX_PATCH_CHARS) + "\n… (patch truncated)";
    }
}
