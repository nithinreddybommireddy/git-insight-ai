package com.gitinsight.githubservice.service;

import com.gitinsight.githubservice.dto.response.CommitAnalyticsResponse;
import com.gitinsight.githubservice.dto.response.RepositoryResponse;
import com.gitinsight.githubservice.service.CommitQualityService.CommitSample;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the Phase 5 commit & code quality analysis service.
 */
class CommitQualityServiceTest {

    private final CommitQualityService service = new CommitQualityService(
            mock(RestClient.class), new GitHubCacheService());

    // ── Commit message quality scoring ──

    @Test
    void messageQualityScoresBlankOrNullAsZero() {
        assertEquals(0, service.scoreMessageQuality(null));
        assertEquals(0, service.scoreMessageQuality(""));
        assertEquals(0, service.scoreMessageQuality("   "));
    }

    @Test
    void messageQualityRewardsConventionalPrefixAndLength() {
        // "feat: add login flow" → conventional +30, length>=10 → +10, words>=4 → +15 → 75
        int score = service.scoreMessageQuality("feat: add login flow");
        assertTrue(score >= 70, "expected >= 70 but was " + score);

        // Short, vague message → low score
        assertTrue(service.scoreMessageQuality("update") < 40);
    }

    @Test
    void messageQualityRewardsMultiLineBody() {
        int single = service.scoreMessageQuality("fix: resolve the auth bug");
        int withBody = service.scoreMessageQuality("fix: resolve the auth bug\n\nAdds token refresh validation and expires-at checks.");
        assertTrue(withBody > single, "multi-line messages should score higher");
    }

    @Test
    void conventionalPrefixDetection() {
        assertTrue(service.hasConventionalPrefix("feat: add thing"));
        assertTrue(service.hasConventionalPrefix("fix(auth): resolve bug"));
        assertTrue(service.hasConventionalPrefix("chore!: bump deps"));
        assertFalse(service.hasConventionalPrefix("Add thing without prefix"));
        assertFalse(service.hasConventionalPrefix("random message"));
        assertEquals("feat", service.extractConventionalType("feat: add thing"));
        assertEquals("fix", service.extractConventionalType("fix(auth): resolve bug"));
        assertNull(service.extractConventionalType("no prefix here"));
    }

    // ── Commit size balance ──

    @Test
    void commitSizeScoresBalancedCommitsHighest() {
        // avg 20 changed lines → balanced → 100
        CommitSample small = new CommitSample("msg", Instant.now().toString(), 15, 5);
        assertEquals(100, service.scoreCommitSize(List.of(small)));

        // avg 2000 lines → monolithic → low
        CommitSample huge = new CommitSample("msg", Instant.now().toString(), 1000, 1000);
        assertTrue(service.scoreCommitSize(List.of(huge)) < 40);

        // empty commit (0 changes) → mediocre
        CommitSample empty = new CommitSample("msg", Instant.now().toString(), 0, 0);
        assertEquals(30, service.scoreCommitSize(List.of(empty)));

        // no commits → 0
        assertEquals(0, service.scoreCommitSize(List.of()));
    }

    // ── Conventional commit rate & aggregation ──

    @Test
    void buildResponseComputesConventionalRateAndTotals() {
        List<CommitSample> samples = List.of(
                new CommitSample("feat: add login", Instant.now().toString(), 50, 10),
                new CommitSample("feat: add logout", Instant.now().toString(), 40, 8),
                new CommitSample("fix: resolve bug", Instant.now().toString(), 30, 5),
                new CommitSample("docs: update readme", Instant.now().toString(), 10, 2),
                new CommitSample("random commit message", Instant.now().toString(), 5, 1));

        CommitAnalyticsResponse res = service.buildResponse("dev", samples, List.of());

        assertEquals(5, res.getTotalCommits());
        assertEquals(135, res.getTotalAdditions());
        assertEquals(26, res.getTotalDeletions());
        assertEquals(80, res.getConventionalCommitRate()); // 4 of 5 conventional
        // most frequent type first (feat appears twice); ties broken alphabetically
        assertEquals("feat", res.getTopCommitTypes().get(0));
        assertTrue(res.getCodeQualityScore() >= 0 && res.getCodeQualityScore() <= 100);
        assertNotNull(res.getExplanation());
        assertNotNull(res.getImprovementSuggestion());
        assertTrue(res.getReposAnalyzed() >= 0);
    }

    @Test
    void buildResponseWithNoCommitsReturnsEmptyAnalytics() {
        CommitAnalyticsResponse res = service.buildResponse("dev", List.of(), List.of());
        assertEquals(0, res.getTotalCommits());
        assertEquals(0, res.getCodeQualityScore());
        assertNotNull(res.getExplanation());
    }

    // ── Weekly activity ──

    @Test
    void weeklyActivityCoversTwelveWeeksZeroFilled() {
        List<CommitSample> samples = List.of(
                new CommitSample("feat: a", Instant.now().toString(), 1, 1),      // this week
                new CommitSample("feat: b", Instant.now().minusSeconds(7L * 86400).toString(), 1, 1), // ~1 week ago
                new CommitSample("feat: c", Instant.now().minusSeconds(7L * 2 * 86400).toString(), 1, 1)); // ~2 weeks ago

        List<CommitAnalyticsResponse.WeeklyActivity> weekly = service.buildWeeklyActivity(samples);

        assertEquals(12, weekly.size());
        int total = weekly.stream().mapToInt(CommitAnalyticsResponse.WeeklyActivity::getCommits).sum();
        assertTrue(total >= 1, "at least the most recent commit should be counted");
        // last bucket is the current ISO week, labelled YYYY-Www
        assertTrue(weekly.get(11).getWeek().matches("\\d{4}-W\\d{2}"));
    }

    @Test
    void weeklyActivityEmptyCommitsAllZero() {
        List<CommitAnalyticsResponse.WeeklyActivity> weekly = service.buildWeeklyActivity(List.of());
        assertEquals(12, weekly.size());
        assertTrue(weekly.stream().allMatch(w -> w.getCommits() == 0));
    }

    // ── analyze() with mocked GitHub API ──

    @Test
    void analyzeParsesCommitsAndComputesMetrics() {
        @SuppressWarnings("rawtypes")
        RestClient.RequestHeadersUriSpec uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        @SuppressWarnings("rawtypes")
        RestClient.RequestHeadersSpec headersSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient restClient = mock(RestClient.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString(), any(Object[].class))).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);

        Map<String, Object> commit1 = new HashMap<>();
        commit1.put("sha", "abc123");
        Map<String, Object> commitInfo = new HashMap<>();
        commitInfo.put("message", "feat: add login flow");
        Map<String, Object> author = new HashMap<>();
        author.put("date", Instant.now().toString());
        commitInfo.put("author", author);
        commit1.put("commit", commitInfo);
        Map<String, Object> stats = new HashMap<>();
        stats.put("additions", 50);
        stats.put("deletions", 10);
        commit1.put("stats", stats);

        when(responseSpec.body(any(ParameterizedTypeReference.class))).thenReturn(List.of(commit1));

        CommitQualityService realService = new CommitQualityService(restClient, new GitHubCacheService());

        RepositoryResponse repo = new RepositoryResponse();
        repo.setName("my-repo");
        repo.setFullName("octocat/my-repo");
        repo.setSize(100);

        CommitAnalyticsResponse res = realService.analyze("octocat", List.of(repo));

        assertEquals(1, res.getTotalCommits());
        assertEquals(50, res.getTotalAdditions());
        assertEquals(10, res.getTotalDeletions());
        assertEquals(1, res.getReposAnalyzed());
        assertEquals("my-repo", res.getRepoBreakdown().get(0).getRepoName());
        assertTrue(res.getCodeQualityScore() > 0);
    }

    @Test
    void analyzeReturnsEmptyForNoRepos() {
        CommitQualityService realService = new CommitQualityService(
                mock(RestClient.class), new GitHubCacheService());
        CommitAnalyticsResponse res = realService.analyze("ghost", List.of());
        assertEquals(0, res.getTotalCommits());
        assertEquals(0, res.getReposAnalyzed());
        assertNotNull(res.getExplanation());
    }

    @Test
    void analyzeSkipsForkedAndArchivedRepos() {
        @SuppressWarnings("rawtypes")
        RestClient.RequestHeadersUriSpec uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        @SuppressWarnings("rawtypes")
        RestClient.RequestHeadersSpec headersSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient restClient = mock(RestClient.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString(), any(Object[].class))).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(any(ParameterizedTypeReference.class))).thenReturn(List.of());

        CommitQualityService realService = new CommitQualityService(restClient, new GitHubCacheService());

        RepositoryResponse fork = new RepositoryResponse();
        fork.setName("forked");
        fork.setFullName("octocat/forked");
        fork.setFork(true);
        fork.setSize(100);

        RepositoryResponse archived = new RepositoryResponse();
        archived.setName("archived");
        archived.setFullName("octocat/archived");
        archived.setArchived(true);
        archived.setSize(100);

        CommitAnalyticsResponse res = realService.analyze("octocat", List.of(fork, archived));
        assertEquals(0, res.getReposAnalyzed());
        assertEquals(0, res.getTotalCommits());
    }
}
