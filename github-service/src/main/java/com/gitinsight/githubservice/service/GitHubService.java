package com.gitinsight.githubservice.service;

import com.gitinsight.githubservice.dto.response.GitHubProfileResponse;
import com.gitinsight.githubservice.dto.response.RepositoryResponse;

import java.util.List;

public interface GitHubService {

    GitHubProfileResponse getProfile(String username);

    List<RepositoryResponse> getRepositories(String username);
}
