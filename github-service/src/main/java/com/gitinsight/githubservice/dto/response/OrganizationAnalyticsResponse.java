package com.gitinsight.githubservice.dto.response;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Organization / team-level analytics.
 * <p>
 * Aggregates a GitHub organization's public profile, repositories, byte-weighted
 * language stack, top contributors, repository health, and recent team activity
 * into a single overview — the team-level counterpart to the per-developer score.
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

    // ── Repository health (over the fetched repo set, up to 100 most-recently-pushed) ──
    private int archivedRepos;
    private int inactiveRepos; // non-fork, non-archived, not pushed within 90 days
    private double forkRatio;  // % of fetched repos that are forks

    // ── Breakdowns ──
    private List<LanguageStat> languages = new ArrayList<>();
    private List<OrgRepoStat> topRepos = new ArrayList<>();
    private List<ContributorStat> topContributors = new ArrayList<>();
    private int activeContributors; // unique contributors across sampled repos

    // ── Team activity (top repos sampled; commits authored in window,
    //    PRs/issues last updated in window per GitHub's `since` filter) ──
    private TeamActivity teamActivity = new TeamActivity();

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

    /**
     * A contributor with their share of the aggregated contributions across the
     * sampled repos (contributionPercent = contributions / total sampled contributions).
     */
    @Data
    public static class ContributorStat {
        private String login;
        private int contributions;
        private String avatarUrl;
        private double contributionPercent;
    }

    /**
     * Commit / pull-request / issue counts over the last 30 and 90 days.
     */
    @Data
    public static class TeamActivity {
        private int commits30d;
        private int commits90d;
        private int pullRequests30d;
        private int pullRequests90d;
        private int issues30d;
        private int issues90d;
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
        r.setArchivedRepos(0);
        r.setInactiveRepos(0);
        r.setForkRatio(0);
        r.setActiveContributors(0);
        r.setTeamActivity(new TeamActivity());
        r.setSummary("No public repository data available for this organization.");
        r.setInsight("The organization has no public repos to analyze, or the name could not be resolved.");
        return r;
    }
}
