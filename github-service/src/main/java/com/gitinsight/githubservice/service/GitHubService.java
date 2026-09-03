package com.gitinsight.githubservice.service;

import com.gitinsight.githubservice.dto.response.GitHubProfileResponse;
import com.gitinsight.githubservice.dto.response.RepositoryContentResponse;
import com.gitinsight.githubservice.dto.response.RepositoryResponse;

import java.util.List;

public interface GitHubService {

    GitHubProfileResponse getProfile(String username);

    List<RepositoryResponse> getRepositories(String username);

    /**
     * List the contents of a repository directory at a specific branch/ref.
     * Used by auth-service's Job Matcher for bounded source-root and nested
     * module discovery. Returns {@code null} when the path does not exist.
     *
     * @param path directory path relative to the repository root, or blank for the root
     * @param ref  branch/tag/commit SHA, or {@code null} for the default branch
     */
    List<RepositoryContentResponse> getContents(String owner, String repo, String path, String ref);
}
