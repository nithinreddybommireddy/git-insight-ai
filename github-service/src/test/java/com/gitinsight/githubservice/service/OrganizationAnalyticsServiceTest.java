package com.gitinsight.githubservice.service;

import com.gitinsight.githubservice.dto.response.OrganizationAnalyticsResponse;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the organization / team-level analytics aggregation.
 */
class OrganizationAnalyticsServiceTest {

    @Test
    void getOverviewAggregatesOrgData() {
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

        when(responseSpec.body(any(ParameterizedTypeReference.class))).thenAnswer(inv -> {
            if (uriTemplate[0] != null && uriTemplate[0].contains("/repos?")) {
                // org repos: 2 real repos + 1 fork (excluded)
                Map<String, Object> big = repo("big-repo", "octo/big-repo", 500, 40, false, false,
                        Instant.now().minusSeconds(10 * 86400).toString());
                Map<String, Object> small = repo("small-repo", "octo/small-repo", 20, 2, false, false,
                        Instant.now().minusSeconds(200 * 86400).toString());
                Map<String, Object> fork = repo("forked", "octo/forked", 999, 1, true, false,
                        Instant.now().toString());
                return List.of(big, small, fork);
            }
            // org profile
            Map<String, Object> profile = new HashMap<>();
            profile.put("login", "octo");
            profile.put("name", "Octo Org");
            profile.put("description", "A demo org");
            profile.put("avatar_url", "https://avatars.example/octo.png");
            profile.put("blog", "https://octo.dev");
            profile.put("location", "San Francisco");
            profile.put("public_repos", 12);
            profile.put("followers", 340);
            profile.put("created_at", "2016-01-01T00:00:00Z");
            return profile;
        });

        GitHubIntegrationService integrationService = mock(GitHubIntegrationService.class);
        when(integrationService.getWeightedLanguageBreakdown(anyList()))
                .thenReturn(List.of(new GitHubIntegrationService.LanguageBreakdown("Java", 80.0, 2),
                        new GitHubIntegrationService.LanguageBreakdown("TypeScript", 20.0, 1)));
        when(integrationService.getAggregateContributors(anyList()))
                .thenReturn(List.of(new GitHubIntegrationService.GitHubContributor("alice", 120, null),
                        new GitHubIntegrationService.GitHubContributor("bob", 60, null)));

        OrganizationAnalyticsService service = new OrganizationAnalyticsService(
                restClient, new GitHubCacheService(), integrationService);

        OrganizationAnalyticsResponse res = service.getOverview("octo");

        assertEquals("octo", res.getLogin());
        assertEquals("Octo Org", res.getName());
        assertEquals("https://octo.dev", res.getBlog());
        assertEquals(340, res.getFollowers());

        // fork excluded → 2 sampled repos
        assertEquals(2, res.getTotalRepos());
        assertEquals(520, res.getTotalStars());
        assertEquals(42, res.getTotalForks());
        assertEquals(260.0, res.getAverageStars());
        assertEquals(1, res.getActiveRepos()); // only big-repo pushed within 90 days

        // top repos sorted by stars desc
        assertEquals("big-repo", res.getTopRepos().get(0).getName());
        assertEquals(500, res.getTopRepos().get(0).getStars());

        // languages + contributors from the integration service
        assertEquals(2, res.getLanguagesCount());
        assertEquals("Java", res.getLanguages().get(0).getLanguage());
        assertEquals(2, res.getTopContributors().size());
        assertEquals("alice", res.getTopContributors().get(0).login());

        assertNotNull(res.getSummary());
        assertNotNull(res.getInsight());
    }

    @Test
    void getOverviewReturnsEmptyWhenProfileFetchFails() {
        @SuppressWarnings("rawtypes")
        RestClient.RequestHeadersUriSpec uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        @SuppressWarnings("rawtypes")
        RestClient.RequestHeadersSpec headersSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient restClient = mock(RestClient.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString(), any(Object[].class))).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(any(ParameterizedTypeReference.class)))
                .thenThrow(new RuntimeException("404 from GitHub"));

        OrganizationAnalyticsService service = new OrganizationAnalyticsService(
                restClient, new GitHubCacheService(), mock(GitHubIntegrationService.class));

        OrganizationAnalyticsResponse res = service.getOverview("ghost-org");
        assertEquals(0, res.getTotalRepos());
        assertEquals("ghost-org", res.getLogin());
        assertNotNull(res.getSummary());
    }

    @Test
    void getOverviewCachesResult() {
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
        when(responseSpec.body(any(ParameterizedTypeReference.class))).thenAnswer(inv -> {
            if (uriTemplate[0] != null && uriTemplate[0].contains("/repos?")) return List.of();
            Map<String, Object> profile = new HashMap<>();
            profile.put("login", "octo");
            profile.put("public_repos", 3);
            return profile;
        });

        OrganizationAnalyticsService service = new OrganizationAnalyticsService(
                restClient, new GitHubCacheService(), mock(GitHubIntegrationService.class));

        OrganizationAnalyticsResponse a = service.getOverview("octo");
        OrganizationAnalyticsResponse b = service.getOverview("octo");

        assertSame(a, b, "second call should hit the cache");
    }

    private static Map<String, Object> repo(String name, String fullName, int stars, int forks,
                                            boolean fork, boolean archived, String pushedAt) {
        Map<String, Object> r = new HashMap<>();
        r.put("name", name);
        r.put("full_name", fullName);
        r.put("stargazers_count", stars);
        r.put("forks_count", forks);
        r.put("fork", fork);
        r.put("archived", archived);
        r.put("language", "Java");
        r.put("pushed_at", pushedAt);
        r.put("description", name + " description");
        return r;
    }
}
