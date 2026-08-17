package com.gitinsight.githubservice.service;

import com.gitinsight.githubservice.dto.response.DeveloperScoreResponse;
import com.gitinsight.githubservice.dto.response.GitHubProfileResponse;
import com.gitinsight.githubservice.dto.response.RepositoryResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Verifies the performance layer: the fully computed developer score is cached
 * per username so repeat reads make zero GitHub calls and zero engine runs.
 */
class DeveloperScoreServiceTest {

    @Test
    void getScoreComputesOnceThenServesFromCache() {
        GitHubService gitHubService = mock(GitHubService.class);
        GitHubIntegrationService integrationService = mock(GitHubIntegrationService.class);
        ScoringEngine scoringEngine = mock(ScoringEngine.class);
        GitHubCacheService cacheService = new GitHubCacheService();

        RepositoryResponse repo = new RepositoryResponse();
        repo.setName("my-repo");
        when(gitHubService.getRepositories("dev")).thenReturn(List.of(repo));
        when(gitHubService.getProfile("dev")).thenReturn(new GitHubProfileResponse());
        when(integrationService.getEnrichedScoreData(anyList()))
                .thenReturn(new GitHubIntegrationService.EnrichedScoreData(List.of(), List.of()));

        DeveloperScoreResponse computed = new DeveloperScoreResponse();
        when(scoringEngine.calculate(anyString(), anyList(), any(), anyList(), anyList(), any()))
                .thenReturn(computed);

        DeveloperScoreService service = new DeveloperScoreService(
                gitHubService, integrationService, scoringEngine, cacheService);

        DeveloperScoreResponse a = service.getScore("dev");
        DeveloperScoreResponse b = service.getScore("dev");

        assertSame(a, b, "second call should return the cached score");
        verify(scoringEngine, times(1)).calculate(anyString(), anyList(), any(), anyList(), anyList(), any());
        verify(gitHubService, times(1)).getRepositories("dev");
        verify(gitHubService, times(1)).getProfile("dev");
    }

    @Test
    void differentUsernamesAreCachedSeparately() {
        GitHubService gitHubService = mock(GitHubService.class);
        GitHubIntegrationService integrationService = mock(GitHubIntegrationService.class);
        ScoringEngine scoringEngine = mock(ScoringEngine.class);
        GitHubCacheService cacheService = new GitHubCacheService();

        when(gitHubService.getRepositories(anyString())).thenReturn(List.of());
        when(gitHubService.getProfile(anyString())).thenReturn(new GitHubProfileResponse());
        when(integrationService.getEnrichedScoreData(anyList()))
                .thenReturn(new GitHubIntegrationService.EnrichedScoreData(List.of(), List.of()));
        when(scoringEngine.calculate(anyString(), anyList(), any(), anyList(), anyList(), any()))
                .thenAnswer(inv -> {
                    DeveloperScoreResponse s = new DeveloperScoreResponse();
                    s.setUsername(inv.getArgument(0));
                    return s;
                });

        DeveloperScoreService service = new DeveloperScoreService(
                gitHubService, integrationService, scoringEngine, cacheService);

        DeveloperScoreResponse a = service.getScore("alice");
        DeveloperScoreResponse b = service.getScore("bob");

        assertSame(a, service.getScore("alice"), "alice's score should still be cached");
        assertSame(b, service.getScore("bob"), "bob's score should still be cached");
        verify(scoringEngine, times(2)).calculate(anyString(), anyList(), any(), anyList(), anyList(), any());
    }
}
