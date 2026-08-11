package com.gitinsight.githubservice.dto.request;

import com.gitinsight.githubservice.dto.response.CommitDiffResponse;

import java.util.List;

/**
 * Phase 6 — Request body for the AI commit-diff review.
 * <p>
 * The frontend sends the already-fetched commit diffs (usually a single commit)
 * so github-service does not re-fetch GitHub data; it only has to reason over
 * the patches and return per-file findings.
 */
public record CommitDiffReviewRequest(
        String username,
        List<CommitDiffResponse> commits
) {
}
