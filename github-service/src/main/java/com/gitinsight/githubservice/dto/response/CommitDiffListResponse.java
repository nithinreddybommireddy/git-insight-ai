package com.gitinsight.githubservice.dto.response;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Phase 6 — List of recent commit diffs for a developer, newest first.
 */
@Data
public class CommitDiffListResponse {

    private String username;
    private int totalCommits;
    private List<CommitDiffResponse> commits = new ArrayList<>();

    public static CommitDiffListResponse empty(String username) {
        CommitDiffListResponse r = new CommitDiffListResponse();
        r.setUsername(username);
        r.setTotalCommits(0);
        return r;
    }
}
