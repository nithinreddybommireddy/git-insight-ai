package com.gitinsight.githubservice.dto.response;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Phase 5 — Commit & Code Quality Analysis.
 * <p>
 * Aggregates raw commit metrics (counts, additions, deletions, message quality,
 * conventional-commit rate, weekly activity) and a normalized 0-100 code quality score.
 * Each sub-metric is computed independently in {@code CommitQualityService} and combined here.
 */
@Data
public class CommitAnalyticsResponse {

    private String username;

    // ── Aggregate commit metrics ──
    private int totalCommits;
    private int totalAdditions;
    private int totalDeletions;
    private double commitsPerWeek;
    private int reposAnalyzed;

    // ── Code quality metrics (0-100) ──
    private int codeQualityScore;
    private int commitMessageQuality;      // 0-100: descriptive, conventional messages
    private int conventionalCommitRate;    // 0-100: % of commits using conventional prefixes
    private double averageMessageLength;
    private int commitSizeScore;           // 0-100: balanced, reviewable commit sizes

    // ── Breakdowns ──
    private List<String> topCommitTypes;   // e.g. ["feat", "fix", "docs"] sorted by frequency
    private List<WeeklyActivity> weeklyActivity;   // last 12 weeks
    private List<RepoCommitStat> repoBreakdown;    // per-repo stats

    // ── Explainability ──
    private String explanation;
    private String improvementSuggestion;
    private String trend; // "up", "stable", "down"

    @Data
    public static class WeeklyActivity {
        private String week;   // ISO week label e.g. "2026-W31"
        private int commits;
    }

    @Data
    public static class RepoCommitStat {
        private String repoName;
        private int totalCommits;
        private int additions;
        private int deletions;
        private int messageQuality;
    }

    public static CommitAnalyticsResponse empty(String username) {
        CommitAnalyticsResponse r = new CommitAnalyticsResponse();
        r.setUsername(username);
        r.setTotalCommits(0);
        r.setTotalAdditions(0);
        r.setTotalDeletions(0);
        r.setCommitsPerWeek(0);
        r.setReposAnalyzed(0);
        r.setCodeQualityScore(0);
        r.setCommitMessageQuality(0);
        r.setConventionalCommitRate(0);
        r.setAverageMessageLength(0);
        r.setCommitSizeScore(0);
        r.setTopCommitTypes(new ArrayList<>());
        r.setWeeklyActivity(new ArrayList<>());
        r.setRepoBreakdown(new ArrayList<>());
        r.setExplanation("No commit data available. Recent commit history could not be analyzed.");
        r.setImprovementSuggestion("Push code regularly with descriptive, conventional commit messages to enable code quality analysis.");
        r.setTrend("stable");
        return r;
    }
}
