package com.gitinsight.githubservice.dto.response;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Phase 6 — Commit-diff data for one commit: commit metadata plus the per-file
 * unified diffs (patches) that were changed in it.
 * <p>
 * Patches are truncated server-side so large files never blow up payloads or
 * the AI prompt. Everything here is deterministic GitHub API data — the AI
 * review built on top of it lives in {@code CommitDiffReviewResponse}.
 */
@Data
public class CommitDiffResponse {

    private String sha;
    private String message;
    private String date;
    private String repoName;
    private int additions;
    private int deletions;
    private int changedFiles;
    private List<FileDiff> files = new ArrayList<>();

    @Data
    public static class FileDiff {
        private String filename;
        /** added | modified | removed | renamed | copied */
        private String status;
        /** set for renames — the path before the change */
        private String previousFilename;
        private int additions;
        private int deletions;
        private int changes;
        /** truncated unified diff */
        private String patch;
    }
}
