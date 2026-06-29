package com.gitinsight.service;

import com.gitinsight.dto.response.GitHubProfileResponse;

public interface GitHubService {

    GitHubProfileResponse getProfile(String username);

}

