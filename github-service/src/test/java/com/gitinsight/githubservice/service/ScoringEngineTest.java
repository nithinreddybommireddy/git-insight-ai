package com.gitinsight.githubservice.service;

import com.gitinsight.githubservice.dto.response.DeveloperScoreResponse;
import com.gitinsight.githubservice.dto.response.RepositoryResponse;
import com.gitinsight.githubservice.service.GitHubIntegrationService.GitHubContributor;
import com.gitinsight.githubservice.service.GitHubIntegrationService.LanguageBreakdown;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the deterministic 10-metric scoring engine.
 */
class ScoringEngineTest {

    private final ScoringEngine engine = new ScoringEngine();

    private RepositoryResponse repo(String name, String lang, int stars, int forks, int openIssues,
                                    int size, int daysSincePush, boolean fork, boolean archived) {
        RepositoryResponse r = new RepositoryResponse();
        r.setName(name);
        r.setFullName("owner/" + name);
        r.setLanguage(lang);
        r.setStars(stars);
        r.setForks(forks);
        r.setOpenIssues(openIssues);
        r.setSize(size);
        r.setFork(fork);
        r.setArchived(archived);
        r.setPushedAt(Instant.now().minusSeconds(daysSincePush * 86400L).toString());
        return r;
    }

    private RepositoryResponse activeRepo(String name, String lang) {
        return repo(name, lang, 0, 0, 0, 200, 2, false, false);
    }

    // ── Weight aggregation ──

    @Test
    void weightsSumToExactlyOne() {
        double sum = ScoringEngine.WEIGHT_CONTRIBUTION_RECENCY
                + ScoringEngine.WEIGHT_COMMIT_FREQUENCY
                + ScoringEngine.WEIGHT_REPOSITORY_HEALTH
                + ScoringEngine.WEIGHT_REPOSITORY_QUALITY
                + ScoringEngine.WEIGHT_CONTRIBUTION_CONSISTENCY
                + ScoringEngine.WEIGHT_LANGUAGE_DIVERSITY
                + ScoringEngine.WEIGHT_COLLABORATION
                + ScoringEngine.WEIGHT_OPEN_SOURCE_IMPACT
                + ScoringEngine.WEIGHT_POPULARITY
                + ScoringEngine.WEIGHT_MAINTENANCE;
        assertEquals(1.0, sum, 1e-9);
    }

    @Test
    void overallScoreIsWeightedSumOfMetrics() {
        List<RepositoryResponse> repos = List.of(
                activeRepo("a", "Java"),
                activeRepo("b", "TypeScript"),
                activeRepo("c", "Python"));
        DeveloperScoreResponse score = engine.calculate("dev", repos, null);

        double expected = score.getContributionRecency() * ScoringEngine.WEIGHT_CONTRIBUTION_RECENCY
                + score.getCommitFrequency() * ScoringEngine.WEIGHT_COMMIT_FREQUENCY
                + score.getRepositoryHealth() * ScoringEngine.WEIGHT_REPOSITORY_HEALTH
                + score.getRepositoryQuality() * ScoringEngine.WEIGHT_REPOSITORY_QUALITY
                + score.getContributionConsistency() * ScoringEngine.WEIGHT_CONTRIBUTION_CONSISTENCY
                + score.getLanguageDiversity() * ScoringEngine.WEIGHT_LANGUAGE_DIVERSITY
                + score.getCollaboration() * ScoringEngine.WEIGHT_COLLABORATION
                + score.getOpenSourceImpact() * ScoringEngine.WEIGHT_OPEN_SOURCE_IMPACT
                + score.getPopularity() * ScoringEngine.WEIGHT_POPULARITY
                + score.getMaintenance() * ScoringEngine.WEIGHT_MAINTENANCE;

        assertEquals(Math.round(expected), score.getOverallScore());
        assertTrue(score.getOverallScore() >= 0 && score.getOverallScore() <= 100);
    }

    @Test
    void overallScoreIsClampedToZeroAndHundred() {
        // All repos extremely active + starred → very high score, still ≤ 100
        List<RepositoryResponse> hot = List.of(
                repo("a", "Java", 5000, 500, 3, 5000, 1, false, false),
                repo("b", "Java", 4000, 400, 2, 4000, 1, false, false));
        DeveloperScoreResponse score = engine.calculate("star", hot, null);
        assertTrue(score.getOverallScore() <= 100);

        // Stale, empty-ish repos → low score, still ≥ 0
        List<RepositoryResponse> cold = List.of(
                repo("a", "Java", 0, 0, 0, 5, 500, false, false),
                repo("b", "Java", 0, 0, 0, 5, 600, false, false));
        DeveloperScoreResponse low = engine.calculate("cold", cold, null);
        assertTrue(low.getOverallScore() >= 0);
    }

    // ── Developer levels ──

    @Test
    void levelBoundariesMatchSpec() {
        assertEquals("Elite 🏆", engine.determineLevel(90));
        assertEquals("Elite 🏆", engine.determineLevel(100));
        assertEquals("Expert 🏅", engine.determineLevel(89));
        assertEquals("Expert 🏅", engine.determineLevel(80));
        assertEquals("Advanced 🚀", engine.determineLevel(79));
        assertEquals("Advanced 🚀", engine.determineLevel(65));
        assertEquals("Proficient 💼", engine.determineLevel(64));
        assertEquals("Proficient 💼", engine.determineLevel(50));
        assertEquals("Intermediate 📘", engine.determineLevel(49));
        assertEquals("Intermediate 📘", engine.determineLevel(35));
        assertEquals("Beginner 🌱", engine.determineLevel(34));
        assertEquals("Beginner 🌱", engine.determineLevel(20));
        assertEquals("Newcomer 🌟", engine.determineLevel(19));
        assertEquals("Newcomer 🌟", engine.determineLevel(0));
    }

    // ── Individual metrics ──

    @Test
    void commitFrequencyUsesDaysSincePushTable() {
        assertEquals(100, engine.calcCommitFrequency(List.of(repo("a", "Java", 0, 0, 0, 100, 7, false, false))).getScore());
        assertEquals(90, engine.calcCommitFrequency(List.of(repo("a", "Java", 0, 0, 0, 100, 14, false, false))).getScore());
        assertEquals(75, engine.calcCommitFrequency(List.of(repo("a", "Java", 0, 0, 0, 100, 30, false, false))).getScore());
        assertEquals(55, engine.calcCommitFrequency(List.of(repo("a", "Java", 0, 0, 0, 100, 60, false, false))).getScore());
        assertEquals(35, engine.calcCommitFrequency(List.of(repo("a", "Java", 0, 0, 0, 100, 90, false, false))).getScore());
        assertEquals(20, engine.calcCommitFrequency(List.of(repo("a", "Java", 0, 0, 0, 100, 180, false, false))).getScore());
        assertEquals(5, engine.calcCommitFrequency(List.of(repo("a", "Java", 0, 0, 0, 100, 200, false, false))).getScore());
    }

    @Test
    void commitFrequencyFallsBackOnMissingPushDate() {
        RepositoryResponse r = new RepositoryResponse();
        r.setName("a");
        r.setFullName("owner/a");
        r.setSize(100);
        // daysSincePush returns 365 for unparseable/missing dates → score 5
        assertEquals(5, engine.calcCommitFrequency(List.of(r)).getScore());
    }

    @Test
    void contributionRecencyFormula() {
        // 1 repo pushed 5 days ago (active30), 1 pushed 60 days ago (active90 only)
        List<RepositoryResponse> repos = List.of(
                repo("a", "Java", 0, 0, 0, 100, 5, false, false),
                repo("b", "Java", 0, 0, 0, 100, 60, false, false));
        // ratio30 = 1/2 → 30, ratio90 = 2/2 → 40 → total 70
        assertEquals(70, engine.calcContributionRecency(repos, 2).getScore());
    }

    @Test
    void languageDiversitySingleLanguageScoresLow() {
        List<RepositoryResponse> repos = List.of(activeRepo("a", "Java"));
        // single language → diversityBonus 12, entropy 0 → 12
        assertEquals(12, engine.calcLanguageDiversity(repos).getScore());
    }

    @Test
    void languageDiversityUsesByteWeightedBreakdown() {
        List<RepositoryResponse> repos = List.of(activeRepo("a", "Java"));
        List<LanguageBreakdown> weighted = List.of(
                new LanguageBreakdown("Java", 50.0, 1),
                new LanguageBreakdown("TypeScript", 30.0, 1),
                new LanguageBreakdown("Python", 20.0, 1));
        int score = engine.calcLanguageDiversity(repos, weighted).getScore();
        // 3 languages → bonus 36 + entropy contribution
        assertTrue(score >= 36 && score <= 100);
    }

    @Test
    void collaborationAddsPointsForExternalContributors() {
        List<RepositoryResponse> effective = List.of(activeRepo("a", "Java"));
        List<GitHubContributor> contributors = List.of(
                new GitHubContributor("alice", 40, null),
                new GitHubContributor("bob", 25, null),
                new GitHubContributor("carol", 10, null));
        // base 20 + external(>0):+5 + (≥3):+5 = 30
        assertEquals(30, engine.calcCollaborationScore(effective, effective, contributors, "dev").getScore());
    }

    @Test
    void openSourceImpactScalesWithStarsForksWatchers() {
        RepositoryResponse r = repo("a", "Java", 100, 30, 0, 500, 2, false, false);
        r.setWatchers(15);
        List<RepositoryResponse> repos = List.of(r);
        // starScore 10 + forkScore 10 + watchScore 15 + sizeScore 3 = 38
        assertEquals(38, engine.calcOpenSourceImpact(repos).getScore());
    }

    // ── Repository filtering rules ──

    @Test
    void calculateExcludesForksArchivedAndEmptyRepos() {
        List<RepositoryResponse> repos = List.of(
                activeRepo("real", "Java"),
                repo("forked", "Go", 0, 0, 0, 100, 2, true, false),
                repo("archived", "Rust", 0, 0, 0, 100, 2, false, true),
                repo("empty", "C", 0, 0, 0, 0, 2, false, false));
        DeveloperScoreResponse score = engine.calculate("dev", repos, null);
        assertEquals(1, score.getTotalRepositories());
        assertEquals("Java", score.getLanguages()[0]); // only the non-fork, non-archived, non-empty repo
    }

    @Test
    void calculateReturnsEmptyResponseForNoRepos() {
        DeveloperScoreResponse score = engine.calculate("ghost", List.of(), null);
        assertEquals(0, score.getOverallScore());
        assertEquals("N/A", score.getLevel());
        assertEquals(0, score.getTotalRepositories());
        assertNull(score.getInsights());
    }

    @Test
    void calculatePopulatesAllTenMetrics() {
        List<RepositoryResponse> repos = List.of(activeRepo("a", "Java"), activeRepo("b", "Python"));
        DeveloperScoreResponse score = engine.calculate("dev", repos, null);
        assertNotNull(score.getContributionRecencyDetails());
        assertNotNull(score.getCommitFrequencyDetails());
        assertNotNull(score.getRepositoryHealthDetails());
        assertNotNull(score.getRepositoryQualityDetails());
        assertNotNull(score.getContributionConsistencyDetails());
        assertNotNull(score.getLanguageDiversityDetails());
        assertNotNull(score.getCollaborationDetails());
        assertNotNull(score.getOpenSourceImpactDetails());
        assertNotNull(score.getPopularityDetails());
        assertNotNull(score.getMaintenanceDetails());
        assertNotNull(score.getInsights());
        assertNotNull(score.getInsights().getOverallAssessment());
    }
}
