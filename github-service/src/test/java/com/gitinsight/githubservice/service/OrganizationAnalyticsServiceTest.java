package com.gitinsight.githubservice.service;

import com.gitinsight.githubservice.dto.response.OrganizationAnalyticsResponse;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.ArrayList;
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
            String tpl = uriTemplate[0];
            if (tpl != null && tpl.contains("/repos?")) {
                // org repos: 2 real repos + 1 fork + 1 archived (both excluded from stats)
                Map<String, Object> big = repo("big-repo", "octo/big-repo", 500, 40, false, false,
                        Instant.now().minusSeconds(10 * 86400).toString());
                Map<String, Object> small = repo("small-repo", "octo/small-repo", 20, 2, false, false,
                        Instant.now().minusSeconds(200 * 86400).toString());
                Map<String, Object> fork = repo("forked", "octo/forked", 999, 1, true, false,
                        Instant.now().toString());
                Map<String, Object> archived = repo("legacy", "octo/legacy", 5, 0, false, true,
                        Instant.now().minusSeconds(400 * 86400).toString());
                return List.of(big, small, fork, archived);
            }
            if (tpl != null && (tpl.contains("/commits?") || tpl.contains("/issues?"))) {
                return List.of(); // no team activity in this fixture
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

        // repository health over the fetched set (4 repos: 1 fork, 1 archived, 1 inactive)
        assertEquals(1, res.getArchivedRepos());
        assertEquals(1, res.getInactiveRepos());
        assertEquals(25.0, res.getForkRatio());

        // top repos sorted by stars desc
        assertEquals("big-repo", res.getTopRepos().get(0).getName());
        assertEquals(500, res.getTopRepos().get(0).getStars());

        // languages + contributors from the integration service
        assertEquals(2, res.getLanguagesCount());
        assertEquals("Java", res.getLanguages().get(0).getLanguage());
        assertEquals(2, res.getTopContributors().size());
        assertEquals("alice", res.getTopContributors().get(0).getLogin());
        assertEquals(66.7, res.getTopContributors().get(0).getContributionPercent());
        assertEquals(33.3, res.getTopContributors().get(1).getContributionPercent());
        assertEquals(2, res.getActiveContributors());

        // team activity present but zero in this fixture
        assertNotNull(res.getTeamActivity());
        assertEquals(0, res.getTeamActivity().getCommits30d());
        assertEquals(0, res.getTeamActivity().getCommits90d());
        assertFalse(res.getSummary().contains("last 30 days"));

        assertNotNull(res.getSummary());
        assertNotNull(res.getInsight());
    }

    @Test
    void teamActivityAggregatesWindowsAcrossRepos() {
        String[] uriTemplate = new String[1];
        // Order-dependent by design: the service fetches the 30d window before the 90d
        // window for each metric (sequential within a repo), so call #1 = 30d, #2 = 90d.
        int[] commitsCalls = new int[1];
        int[] issuesCalls = new int[1];
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
            String tpl = uriTemplate[0];
            if (tpl != null && tpl.contains("/repos?")) {
                return List.of(repo("one", "octo/one", 100, 5, false, false,
                        Instant.now().toString()));
            }
            if (tpl != null && tpl.contains("/commits?")) {
                commitsCalls[0]++;
                return commitsCalls[0] == 1 ? commits(5) : commits(9); // 30d, then 90d
            }
            if (tpl != null && tpl.contains("/issues?")) {
                issuesCalls[0]++;
                // 30d: 1 PR + 1 issue; 90d: 1 PR + 3 issues
                return issuesCalls[0] == 1
                        ? List.of(prItem(), issueItem())
                        : List.of(prItem(), issueItem(), issueItem(), issueItem());
            }
            // org profile
            Map<String, Object> profile = new HashMap<>();
            profile.put("login", "octo");
            profile.put("public_repos", 3);
            return profile;
        });

        GitHubIntegrationService integrationService = mock(GitHubIntegrationService.class);
        when(integrationService.getWeightedLanguageBreakdown(anyList())).thenReturn(List.of());
        when(integrationService.getAggregateContributors(anyList())).thenReturn(List.of());

        OrganizationAnalyticsService service = new OrganizationAnalyticsService(
                restClient, new GitHubCacheService(), integrationService);

        OrganizationAnalyticsResponse res = service.getOverview("octo");

        assertEquals(9, res.getTeamActivity().getCommits90d());
        assertEquals(5, res.getTeamActivity().getCommits30d());
        assertEquals(1, res.getTeamActivity().getPullRequests90d());
        assertEquals(3, res.getTeamActivity().getIssues90d());
        assertEquals(1, res.getTeamActivity().getPullRequests30d());
        assertEquals(1, res.getTeamActivity().getIssues30d());

        // the deterministic summary surfaces the 30-day activity
        assertTrue(res.getSummary().contains("last 30 days"));
    }

    @Test
    void teamActivityDegradesToZerosWhenFetchesFail() {
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
            String tpl = uriTemplate[0];
            if (tpl != null && tpl.contains("/repos?")) {
                return List.of(repo("one", "octo/one", 100, 5, false, false,
                        Instant.now().toString()));
            }
            if (tpl != null && (tpl.contains("/commits?") || tpl.contains("/issues?"))) {
                throw new RuntimeException("GitHub rate limit reached");
            }
            Map<String, Object> profile = new HashMap<>();
            profile.put("login", "octo");
            profile.put("public_repos", 3);
            return profile;
        });

        GitHubIntegrationService integrationService = mock(GitHubIntegrationService.class);
        when(integrationService.getWeightedLanguageBreakdown(anyList())).thenReturn(List.of());
        when(integrationService.getAggregateContributors(anyList())).thenReturn(List.of());

        OrganizationAnalyticsService service = new OrganizationAnalyticsService(
                restClient, new GitHubCacheService(), integrationService);

        OrganizationAnalyticsResponse res = service.getOverview("octo");

        // overview still renders with zeroed team activity instead of failing
        assertEquals(1, res.getTotalRepos());
        assertNotNull(res.getTeamActivity());
        assertEquals(0, res.getTeamActivity().getCommits90d());
        assertEquals(0, res.getTeamActivity().getPullRequests90d());
        assertEquals(0, res.getTeamActivity().getIssues90d());
        assertNotNull(res.getSummary());
        assertFalse(res.getSummary().contains("last 30 days"));
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

    private static List<Map<String, Object>> commits(int n) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(Map.<String, Object>of("sha", "sha-" + i));
        }
        return list;
    }

    private static Map<String, Object> prItem() {
        Map<String, Object> m = new HashMap<>();
        m.put("pull_request", true);
        return m;
    }

    private static Map<String, Object> issueItem() {
        return new HashMap<>(); // real GitHub API omits the pull_request key for issues
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
