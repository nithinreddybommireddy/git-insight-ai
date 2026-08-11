package com.gitinsight.githubservice.dto.response;

import com.gitinsight.githubservice.service.GitHubIntegrationService.GitHubContributor;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Organization / team-level analytics.
 * <p>
 * Aggregates a GitHub organization's public profile, repositories, byte-weighted
 * language stack, and top contributors into a single overview — the team-level
 * counterpart to the per-developer score.
 */
@Data
public class OrganizationAnalyticsResponse {

    // ── Org profile ──
    private String login;
    private String name;
    private String description;
    private String avatarUrl;
    private String blog;
    private String location;
    private int publicRepos;
    private int followers;
    private String createdAt;

    // ── Aggregated repo stats (non-fork, non-archived) ──
    private int totalRepos;
    private int totalStars;
    private int totalForks;
    private double averageStars;
    private int languagesCount;
    private int activeRepos;   // pushed within the last 90 days

    // ── Breakdowns ──
    private List<LanguageStat> languages = new ArrayList<>();
    private List<OrgRepoStat> topRepos = new ArrayList<>();
    private List<GitHubContributor> topContributors = new ArrayList<>();

    // ── Explainability ──
    private String summary;
    private String insight;

    @Data
    public static class LanguageStat {
        private String language;
        private double percentage;
        private int repos;
    }

    @Data
    public static class OrgRepoStat {
        private String name;
        private String description;
        private String language;
        private int stars;
        private int forks;
        private String pushedAt;
    }

    public static OrganizationAnalyticsResponse empty(String login) {
        OrganizationAnalyticsResponse r = new OrganizationAnalyticsResponse();
        r.setLogin(login);
        r.setName(null);
        r.setDescription(null);
        r.setAvatarUrl("");
        r.setPublicRepos(0);
        r.setFollowers(0);
        r.setTotalRepos(0);
        r.setTotalStars(0);
        r.setTotalForks(0);
        r.setAverageStars(0);
        r.setLanguagesCount(0);
        r.setActiveRepos(0);
        r.setSummary("No public repository data available for this organization.");
        r.setInsight("The organization has no public repos to analyze, or the name could not be resolved.");
        return r;
    }
}
