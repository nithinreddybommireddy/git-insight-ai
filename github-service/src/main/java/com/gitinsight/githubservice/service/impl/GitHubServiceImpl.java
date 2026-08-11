package com.gitinsight.githubservice.service.impl;

import com.gitinsight.githubservice.config.GitHubRateLimitInterceptor;
import com.gitinsight.githubservice.dto.response.GitHubProfileResponse;
import com.gitinsight.githubservice.dto.response.GitHubRepoApiResponse;
import com.gitinsight.githubservice.dto.response.GitHubUserApiResponse;
import com.gitinsight.githubservice.dto.response.RepositoryResponse;
import com.gitinsight.githubservice.service.GitHubCacheService;
import com.gitinsight.githubservice.service.GitHubService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class GitHubServiceImpl implements GitHubService {

    private static final String GITHUB_API_BASE = "https://api.github.com";
    private static final Duration BASE_TTL = Duration.ofMinutes(5);

    private final RestClient restClient;
    private final GitHubCacheService cacheService;

    public GitHubServiceImpl(@Value("${github.token:}") String githubToken,
                             GitHubRateLimitInterceptor rateLimitInterceptor,
                             GitHubCacheService cacheService) {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(GITHUB_API_BASE)
                .defaultHeader("Accept", "application/vnd.github.v3+json")
                .defaultHeader("User-Agent", "GitInsight-AI/1.0");

        if (StringUtils.hasText(githubToken)) {
            builder.defaultHeader("Authorization", "Bearer " + githubToken);
        }
        builder.requestInterceptor(rateLimitInterceptor);

        this.restClient = builder.build();
        this.cacheService = cacheService;
    }

    @Override
    public GitHubProfileResponse getProfile(String username) {
        // These two calls are the base of every score/AI/analytics request, so
        // caching them removes 2 GitHub API calls from each request (and makes
        // the 30-min full-score cache in DeveloperScoreService possible).
        String cacheKey = "profile:" + username;
        GitHubProfileResponse cached = cacheService.get(cacheKey);
        if (cached != null) return cached;

        GitHubUserApiResponse apiResponse;

        try {
            apiResponse = restClient.get()
                    .uri("/users/{username}", username)
                    .retrieve()
                    .body(GitHubUserApiResponse.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new RuntimeException("GitHub user '" + username + "' not found.");
        } catch (HttpClientErrorException.TooManyRequests e) {
            throw new RuntimeException(
                    "GitHub API rate limit exceeded. Configure a GitHub Personal Access Token (GITHUB_TOKEN) or wait until the rate limit resets."
            );
        } catch (HttpClientErrorException.Forbidden e) {
            throw new RuntimeException(
                    "GitHub API rate limit exceeded. Configure a GitHub Personal Access Token (GITHUB_TOKEN) or wait until the rate limit resets."
            );
        }

        if (apiResponse == null) {
            throw new RuntimeException("Failed to fetch profile for user: " + username);
        }

        GitHubProfileResponse profile = mapToProfileResponse(apiResponse);
        cacheService.put(cacheKey, profile, BASE_TTL);
        return profile;
    }

    @Override
    public List<RepositoryResponse> getRepositories(String username) {
        String cacheKey = "repos:" + username;
        List<RepositoryResponse> cached = cacheService.get(cacheKey);
        if (cached != null) return cached;

        List<GitHubRepoApiResponse> apiRepos;

        try {
            apiRepos = restClient.get()
                    .uri("/users/{username}/repos?sort=updated&per_page=100", username)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<GitHubRepoApiResponse>>() {});
        } catch (HttpClientErrorException.NotFound e) {
            throw new RuntimeException("GitHub user '" + username + "' not found.");
        } catch (HttpClientErrorException.TooManyRequests e) {
            throw new RuntimeException(
                    "GitHub API rate limit exceeded. Configure a GitHub Personal Access Token (GITHUB_TOKEN) or wait until the rate limit resets."
            );
        } catch (HttpClientErrorException.Forbidden e) {
            throw new RuntimeException(
                    "GitHub API rate limit exceeded. Configure a GitHub Personal Access Token (GITHUB_TOKEN) or wait until the rate limit resets."
            );
        }

        if (apiRepos == null) {
            return List.of();
        }

        List<RepositoryResponse> repos = apiRepos.stream()
                .map(this::mapToRepoResponse)
                .sorted(Comparator.comparingInt(RepositoryResponse::getStars).reversed())
                .collect(Collectors.toList());
        cacheService.put(cacheKey, repos, BASE_TTL);
        return repos;
    }

    private RepositoryResponse mapToRepoResponse(GitHubRepoApiResponse api) {
        RepositoryResponse repo = new RepositoryResponse();

        repo.setGithubId(api.getGithubId());
        repo.setName(api.getName());
        repo.setFullName(api.getFullName());
        repo.setDescription(api.getDescription());
        repo.setHtmlUrl(api.getHtmlUrl());
        repo.setHomepage(api.getHomepage());
        repo.setLanguage(api.getLanguage());
        repo.setFork(api.isFork());
        repo.setDefaultBranch(api.getDefaultBranch());
        repo.setStars(api.getStargazersCount());
        repo.setForks(api.getForksCount());
        repo.setOpenIssues(api.getOpenIssuesCount());
        repo.setWatchers(api.getWatchersCount());
        repo.setSize(api.getSize());
        repo.setTopics(api.getTopics() != null ? api.getTopics() : new String[0]);
        repo.setHasLicense(api.getLicense() != null);
        repo.setCreatedAt(api.getCreatedAt());
        repo.setUpdatedAt(api.getUpdatedAt());
        repo.setPushedAt(api.getPushedAt());
        // Preserve real values so the ScoringEngine's repository filtering rules work
        // (fork/archived/template/generated/empty repos must be excluded from scoring)
        repo.setArchived(api.isArchived());
        repo.setDisabled(api.isDisabled());

        // Calculate health scores
        repo.setPopularityScore(calculatePopularityScore(api));
        repo.setDocumentationScore(calculateDocumentationScore(api));
        repo.setMaintenanceScore(calculateMaintenanceScore(api));
        repo.setActivityScore(calculateActivityScore(api));
        repo.setHealthScore(calculateOverallHealth(repo));

        return repo;
    }

    private int calculatePopularityScore(GitHubRepoApiResponse api) {
        int score = 0;
        if (api.getStargazersCount() > 0) score += 25;
        if (api.getStargazersCount() >= 10) score += 15;
        if (api.getStargazersCount() >= 100) score += 15;
        if (api.getForksCount() > 0) score += 15;
        if (api.getForksCount() >= 5) score += 10;
        if (api.getWatchersCount() > 0) score += 10;
        if (api.getStargazersCount() >= 1000) score += 10;
        return Math.min(score, 100);
    }

    private int calculateDocumentationScore(GitHubRepoApiResponse api) {
        int score = 40; // Base score for having a repo
        if (api.getTopics() != null && api.getTopics().length > 0) score += 15;
        if (api.getLicense() != null) score += 15;
        if (api.getDescription() != null && api.getDescription().length() > 10) score += 15;
        if (api.getHomepage() != null && !api.getHomepage().isEmpty()) score += 15;
        return Math.min(score, 100);
    }

    private int calculateMaintenanceScore(GitHubRepoApiResponse api) {
        int score = 50;
        try {
            if (api.getPushedAt() != null) {
                Instant pushed = ZonedDateTime.parse(api.getPushedAt()).toInstant();
                long daysSincePush = Duration.between(pushed, Instant.now()).toDays();
                if (daysSincePush < 7) score += 25;
                else if (daysSincePush < 30) score += 15;
                else if (daysSincePush < 90) score += 5;
                else score -= 20;
            }
            if (api.getOpenIssuesCount() <= 3) score += 15;
            else if (api.getOpenIssuesCount() <= 10) score += 10;
            else if (api.getOpenIssuesCount() > 50) score -= 10;
        } catch (Exception e) {
            // If date parsing fails, keep base score
        }
        return Math.max(0, Math.min(score, 100));
    }

    private int calculateActivityScore(GitHubRepoApiResponse api) {
        int score = 30;
        if (api.getSize() > 100) score += 15;
        if (api.getSize() > 1000) score += 5;
        try {
            if (api.getPushedAt() != null) {
                Instant pushed = ZonedDateTime.parse(api.getPushedAt()).toInstant();
                long monthsSincePush = Duration.between(pushed, Instant.now()).toDays() / 30;
                if (monthsSincePush < 1) score += 25;
                else if (monthsSincePush < 3) score += 15;
                else if (monthsSincePush < 6) score += 10;
            }
        } catch (Exception e) {
            // Keep base score
        }
        return Math.min(score, 100);
    }

    private int calculateOverallHealth(RepositoryResponse repo) {
        return (int) Math.round(
                repo.getPopularityScore() * 0.25 +
                repo.getDocumentationScore() * 0.25 +
                repo.getMaintenanceScore() * 0.30 +
                repo.getActivityScore() * 0.20
        );
    }

    private GitHubProfileResponse mapToProfileResponse(GitHubUserApiResponse api) {
        GitHubProfileResponse profile = new GitHubProfileResponse();

        profile.setGithubId(api.getGithubId());
        profile.setUsername(api.getUsername());
        profile.setName(api.getName());
        profile.setAvatarUrl(api.getAvatarUrl());
        profile.setProfileUrl(api.getProfileUrl());
        profile.setBio(api.getBio());
        profile.setCompany(api.getCompany());
        profile.setLocation(api.getLocation());
        profile.setWebsite(api.getWebsite());
        profile.setEmail(api.getEmail());
        profile.setTwitterUsername(api.getTwitterUsername());
        profile.setHireable(api.getHireable());
        profile.setPublicRepositories(api.getPublicRepositories());
        profile.setPublicGists(api.getPublicGists());
        profile.setFollowers(api.getFollowers());
        profile.setFollowing(api.getFollowing());
        profile.setCreatedAt(api.getCreatedAt());
        profile.setUpdatedAt(api.getUpdatedAt());

        return profile;
    }
}
