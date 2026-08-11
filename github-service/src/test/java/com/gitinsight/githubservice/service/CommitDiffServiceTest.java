package com.gitinsight.githubservice.service;

import com.gitinsight.githubservice.dto.request.CommitDiffReviewRequest;
import com.gitinsight.githubservice.dto.response.CommitDiffListResponse;
import com.gitinsight.githubservice.dto.response.CommitDiffResponse;
import com.gitinsight.githubservice.dto.response.CommitDiffReviewResponse;
import com.gitinsight.githubservice.dto.response.RepositoryResponse;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the Phase 6 commit-diff service and its rule-based fallback review.
 */
class CommitDiffServiceTest {

    // ── Patch truncation ──

    @Test
    void truncatePatchKeepsShortPatchesAndCutsLongOnes() {
        assertEquals("short", CommitDiffService.truncatePatch("short"));

        String big = "x".repeat(7000);
        String truncated = CommitDiffService.truncatePatch(big);
        assertTrue(truncated.length() < big.length());
        assertTrue(truncated.endsWith("… (patch truncated)"));
    }

    // ── getRecentDiffs() with mocked GitHub API ──

    @Test
    void getRecentDiffsParsesCommitDetails() {
        String[] uriTemplate = new String[1];
        @SuppressWarnings("rawtypes")
        RestClient.RequestHeadersUriSpec uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        @SuppressWarnings("rawtypes")
        RestClient.RequestHeadersSpec headersSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient restClient = mock(RestClient.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString(), any(Object[].class))).thenAnswer(inv -> {
            uriTemplate[0] = inv.getArgument(0);
            return headersSpec;
        });
        when(headersSpec.retrieve()).thenReturn(responseSpec);

        // commit list response → one sha
        Map<String, Object> listEntry = new HashMap<>();
        listEntry.put("sha", "abc123");
        when(responseSpec.body(any(ParameterizedTypeReference.class))).thenAnswer(inv -> {
            if (uriTemplate[0] != null && uriTemplate[0].contains("?author=")) {
                return List.of(listEntry);
            }
            // commit detail response
            Map<String, Object> detail = new HashMap<>();
            detail.put("sha", "abc123");
            Map<String, Object> commitInfo = new HashMap<>();
            commitInfo.put("message", "feat: add commit diff review\n\nReviews per-file patches with Gemini.");
            Map<String, Object> author = new HashMap<>();
            author.put("date", "2026-08-01T10:00:00Z");
            commitInfo.put("author", author);
            detail.put("commit", commitInfo);

            Map<String, Object> file = new HashMap<>();
            file.put("filename", "src/Reviewer.java");
            file.put("status", "modified");
            file.put("additions", 42);
            file.put("deletions", 3);
            file.put("changes", 45);
            file.put("patch", "@@ -1,5 +1,47 @@\n+ new code");
            detail.put("files", List.of(file));
            return detail;
        });

        CommitDiffService service = new CommitDiffService(restClient, new GitHubCacheService());

        RepositoryResponse repo = new RepositoryResponse();
        repo.setName("my-repo");
        repo.setFullName("octocat/my-repo");
        repo.setSize(100);

        CommitDiffListResponse res = service.getRecentDiffs("octocat", List.of(repo), 15);

        assertEquals(1, res.getTotalCommits());
        CommitDiffResponse diff = res.getCommits().get(0);
        assertEquals("abc123", diff.getSha());
        assertEquals("my-repo", diff.getRepoName());
        assertEquals("feat: add commit diff review", diff.getMessage().lines().findFirst().orElse(""));
        assertEquals("2026-08-01T10:00:00Z", diff.getDate());
        assertEquals(42, diff.getAdditions());
        assertEquals(3, diff.getDeletions());
        assertEquals(1, diff.getChangedFiles());
        assertEquals("src/Reviewer.java", diff.getFiles().get(0).getFilename());
        assertEquals("modified", diff.getFiles().get(0).getStatus());
        assertEquals(45, diff.getFiles().get(0).getChanges());
        assertNotNull(diff.getFiles().get(0).getPatch());
    }

    @Test
    void getRecentDiffsReturnsEmptyForNoRepos() {
        CommitDiffService service = new CommitDiffService(mock(RestClient.class), new GitHubCacheService());
        CommitDiffListResponse res = service.getRecentDiffs("ghost", List.of(), 15);
        assertEquals(0, res.getTotalCommits());
        assertEquals("ghost", res.getUsername());
    }

    @Test
    void getRecentDiffsSkipsForkedAndArchivedRepos() {
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

        CommitDiffService service = new CommitDiffService(restClient, new GitHubCacheService());

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

        CommitDiffListResponse res = service.getRecentDiffs("octocat", List.of(fork, archived), 15);
        assertEquals(0, res.getTotalCommits());
    }

    // ── Deterministic fallback review ──

    @Test
    void deterministicReviewScoresBalancedDiffWell() {
        CommitDiffResponse commit = new CommitDiffResponse();
        commit.setSha("sha1");
        commit.setRepoName("my-repo");
        commit.setMessage("fix: resolve auth token expiry");
        CommitDiffResponse.FileDiff file = new CommitDiffResponse.FileDiff();
        file.setFilename("AuthService.java");
        file.setStatus("modified");
        file.setAdditions(30);
        file.setDeletions(5);
        file.setChanges(35);
        file.setPatch("@@ -1,10 +1,35 @@\n");
        commit.getFiles().add(file);
        commit.setChangedFiles(1);

        CommitDiffReviewResponse res = CommitDiffReviewResponse.deterministic(
                new CommitDiffReviewRequest("octocat", List.of(commit)));

        assertFalse(res.isAiEnabled());
        assertTrue(res.getOverallScore() >= 70, "balanced diff should score well, got " + res.getOverallScore());
        assertEquals(1, res.getFileReviews().size());
        assertEquals("AuthService.java", res.getFileReviews().get(0).getFilename());
        assertTrue(res.getFileReviews().get(0).getScore() >= 70);
        assertFalse(res.getFileReviews().get(0).getIssues().isEmpty());
    }

    @Test
    void deterministicReviewPenalizesMonolithicDiff() {
        CommitDiffResponse commit = new CommitDiffResponse();
        commit.setMessage("update");
        CommitDiffResponse.FileDiff file = new CommitDiffResponse.FileDiff();
        file.setFilename("Huge.java");
        file.setStatus("modified");
        file.setAdditions(1000);
        file.setDeletions(1000);
        file.setChanges(2000);
        file.setPatch("@@ -1,5 +1,1000 @@\n");
        commit.getFiles().add(file);
        commit.setChangedFiles(1);

        CommitDiffReviewResponse res = CommitDiffReviewResponse.deterministic(
                new CommitDiffReviewRequest("octocat", List.of(commit)));

        assertTrue(res.getOverallScore() < 50, "mega-diff should score low, got " + res.getOverallScore());
        assertTrue(res.getFileReviews().get(0).getIssues().stream()
                .anyMatch(i -> i.contains("Large diff")));
    }

    @Test
    void deterministicReviewHandlesEmptyInput() {
        CommitDiffReviewResponse res = CommitDiffReviewResponse.deterministic(
                new CommitDiffReviewRequest("octocat", List.of()));
        assertEquals(0, res.getOverallScore());
        assertTrue(res.getKeyIssues().stream().anyMatch(i -> i.contains("No diffs")));
    }
}
