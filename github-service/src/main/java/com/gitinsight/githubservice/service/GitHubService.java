package com.gitinsight.githubservice.service;

import com.gitinsight.githubservice.dto.response.GitHubProfileResponse;

public interface GitHubService {

    GitHubProfileResponse getProfile(String username);
}
