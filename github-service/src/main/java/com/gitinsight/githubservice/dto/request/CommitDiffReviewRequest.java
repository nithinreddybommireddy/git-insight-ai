package com.gitinsight.githubservice.dto.request;

import com.gitinsight.githubservice.dto.response.CommitDiffResponse;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Phase 6 — Request body for the AI commit-diff review.
 * <p>
 * The frontend sends the already-fetched commit diffs (usually a single commit)
 * so github-service does not re-fetch GitHub data; it only has to reason over
 * the patches and return per-file findings.
 * <p>
 * Bounded at the HTTP layer: Gemini reasoning over patches is expensive, so the
 * commit count is capped (mirrors {@code GeminiService.MAX_COMMIT_DIFF_COMMITS}).
 */
public record CommitDiffReviewRequest(
        @Size(max = 39, message = "Username must be under 39 characters")
        String username,

        @Size(max = 3, message = "At most 3 commits can be reviewed at once")
        List<CommitDiffResponse> commits
) {
}
