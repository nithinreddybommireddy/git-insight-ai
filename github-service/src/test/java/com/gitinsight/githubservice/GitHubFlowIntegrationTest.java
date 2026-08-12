package com.gitinsight.githubservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gitinsight.common.dto.response.ApiResponse;
import com.gitinsight.githubservice.dto.response.DeveloperScoreResponse;
import com.gitinsight.githubservice.dto.response.GitHubProfileResponse;
import com.gitinsight.githubservice.dto.response.OrganizationAnalyticsResponse;
import com.gitinsight.githubservice.dto.response.RepositoryResponse;
import com.gitinsight.githubservice.entity.ScoreHistory;
import com.gitinsight.githubservice.repository.ScoreHistoryRepository;
import com.gitinsight.githubservice.service.CommitDiffService;
import com.gitinsight.githubservice.service.CommitQualityService;
import com.gitinsight.githubservice.service.GeminiService;
import com.gitinsight.githubservice.service.GitHubIntegrationService;
import com.gitinsight.githubservice.service.GitHubService;
import com.gitinsight.githubservice.service.OrganizationAnalyticsService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for github-service through the real HTTP + security + cache +
 * scoring + JPA stack (H2). Only the external boundaries (GitHub REST, Gemini)
 * are mocked — the controllers, ApiResponse envelope, DeveloperScoreService
 * caching, ScoringEngine, and report persistence all run for real.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GitHubFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ScoreHistoryRepository scoreHistoryRepository;

    @MockitoBean
    private GitHubService gitHubService;

    @MockitoBean
    private GitHubIntegrationService integrationService;

    @MockitoBean
    private GeminiService geminiService;

    @MockitoBean
    private OrganizationAnalyticsService organizationAnalyticsService;

    @MockitoBean
    private CommitQualityService commitQualityService;

    @MockitoBean
    private CommitDiffService commitDiffService;

    @BeforeEach
    void resetDb() {
        scoreHistoryRepository.deleteAll();
    }

    // ── Fixtures ──

    private RepositoryResponse repo(String name, String language, int stars, int size) {
        RepositoryResponse r = new RepositoryResponse();
        r.setGithubId(100L + stars);
        r.setName(name);
        r.setFullName("owner/" + name);
        r.setDescription("A fixture repository with a fairly descriptive sentence for scoring.");
        r.setHtmlUrl("https://github.com/owner/" + name);
        r.setHomepage("https://example.com");
        r.setLanguage(language);
        r.setFork(false);
        r.setDefaultBranch("main");
        r.setStars(stars);
        r.setForks(0);
        r.setOpenIssues(0);
        r.setWatchers(0);
        r.setSize(size);
        r.setTopics(new String[]{"java"});
        r.setHasLicense(true);
        r.setCreatedAt("2021-01-01T00:00:00Z");
        r.setUpdatedAt("2024-01-01T00:00:00Z");
        r.setPushedAt(Instant.now().toString());
        r.setArchived(false);
        r.setDisabled(false);
        return r;
    }

    private GitHubProfileResponse profile(String username) {
        GitHubProfileResponse p = new GitHubProfileResponse();
        p.setGithubId(42L);
        p.setUsername(username);
        p.setName("Test Developer");
        p.setAvatarUrl("https://avatars.githubusercontent.com/u/42");
        p.setProfileUrl("https://github.com/" + username);
        p.setBio("Building things with Java and React.");
        p.setLocation("Remote");
        p.setHireable(true);
        p.setPublicRepositories(2);
        p.setPublicGists(3);
        p.setFollowers(15);
        p.setFollowing(8);
        p.setCreatedAt("2020-05-01T00:00:00Z");
        p.setUpdatedAt("2024-01-01T00:00:00Z");
        return p;
    }

    private GitHubIntegrationService.EnrichedScoreData enriched() {
        return new GitHubIntegrationService.EnrichedScoreData(
                List.of(new GitHubIntegrationService.LanguageBreakdown("Java", 62.5, 2),
                        new GitHubIntegrationService.LanguageBreakdown("TypeScript", 37.5, 1)),
                List.of(new GitHubIntegrationService.GitHubContributor("octocat", 120, "https://avatars.example/u.png")));
    }

    private OrganizationAnalyticsResponse orgOverview() {
        OrganizationAnalyticsResponse r = OrganizationAnalyticsResponse.empty("vercel");
        r.setName("Vercel");
        r.setDescription("Develop. Preview. Ship.");
        r.setAvatarUrl("https://avatars.example/vercel.png");
        r.setPublicRepos(42);
        r.setTotalRepos(42);
        r.setTotalStars(125000);
        r.setActiveRepos(18);
        r.setArchivedRepos(5);
        r.setInactiveRepos(19);
        r.setForkRatio(16.7);
        r.setActiveContributors(18);

        OrganizationAnalyticsResponse.LanguageStat java = new OrganizationAnalyticsResponse.LanguageStat();
        java.setLanguage("TypeScript");
        java.setPercentage(60.0);
        java.setRepos(25);
        r.setLanguages(List.of(java));

        OrganizationAnalyticsResponse.OrgRepoStat top = new OrganizationAnalyticsResponse.OrgRepoStat();
        top.setName("next.js");
        top.setStars(130000);
        top.setLanguage("TypeScript");
        r.setTopRepos(List.of(top));

        OrganizationAnalyticsResponse.ContributorStat c = new OrganizationAnalyticsResponse.ContributorStat();
        c.setLogin("vercel-bot");
        c.setContributions(5000);
        c.setContributionPercent(28.4);
        r.setTopContributors(List.of(c));

        r.getTeamActivity().setCommits30d(184);
        r.getTeamActivity().setCommits90d(612);
        r.getTeamActivity().setPullRequests30d(42);
        r.getTeamActivity().setPullRequests90d(137);
        r.getTeamActivity().setIssues30d(31);
        r.getTeamActivity().setIssues90d(96);
        r.setSummary("Vercel is shipping across 42 public repos.");
        return r;
    }

    private void stubDeveloper(String username) {
        when(gitHubService.getRepositories(username))
                .thenReturn(List.of(repo("core-api", "Java", 45, 400), repo("web-app", "TypeScript", 12, 300)));
        when(gitHubService.getProfile(username)).thenReturn(profile(username));
        when(integrationService.getEnrichedScoreData(anyList()))
                .thenReturn(enriched());
    }

    /** Valid auth-service-style JWT signed with the test profile's JWT_SECRET. */
    private String validToken() {
        return Jwts.builder()
                .subject("1")
                .claim("email", "user@example.com")
                .claim("role", "USER")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3_600_000))
                .signWith(Keys.hmacShaKeyFor(
                        "integration-test-secret-key-at-least-32-bytes-long".getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    // ── Health / security surface ──

    @Test
    void healthEndpointResponds() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ── Developer analysis ──

    @Test
    void profileEndpointReturnsMappedProfile() throws Exception {
        when(gitHubService.getProfile("octocat")).thenReturn(profile("octocat"));

        mockMvc.perform(get("/api/github/profile/octocat"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value("octocat"))
                .andExpect(jsonPath("$.data.followers").value(15));
    }

    @Test
    void scoreEndpointComputesRealScore() throws Exception {
        stubDeveloper("dev");

        mockMvc.perform(get("/api/github/dev/score"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value("dev"))
                .andExpect(jsonPath("$.data.overallScore").isNumber())
                .andExpect(jsonPath("$.data.totalRepositories").value(2));
    }

    @Test
    void scoreIsCachedAcrossRequests() throws Exception {
        stubDeveloper("cacheddev");

        MvcResult first = mockMvc.perform(get("/api/github/cacheddev/score")).andExpect(status().isOk()).andReturn();
        MvcResult second = mockMvc.perform(get("/api/github/cacheddev/score")).andExpect(status().isOk()).andReturn();

        assertThat(scoreData(first).getOverallScore()).isEqualTo(scoreData(second).getOverallScore());
        // The 30-min score cache absorbs the second request: underlying calls happen once.
        verify(gitHubService, times(1)).getRepositories("cacheddev");
        verify(gitHubService, times(1)).getProfile("cacheddev");
        verify(integrationService, times(1)).getEnrichedScoreData(anyList());
    }

    private DeveloperScoreResponse scoreData(MvcResult result) throws Exception {
        ApiResponse<DeveloperScoreResponse> envelope = objectMapper.readValue(
                result.getResponse().getContentAsString().getBytes(),
                objectMapper.getTypeFactory().constructParametricType(ApiResponse.class, DeveloperScoreResponse.class));
        return envelope.getData();
    }

    @Test
    void unknownUserReturns404Envelope() throws Exception {
        when(gitHubService.getRepositories("ghost"))
                .thenThrow(new RuntimeException("GitHub user 'ghost' not found."));

        mockMvc.perform(get("/api/github/ghost/score"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void rateLimitExhaustionReturns429() throws Exception {
        when(gitHubService.getRepositories("limited"))
                .thenThrow(new RuntimeException("GitHub API rate limit exceeded. Configure a GitHub Personal Access Token (GITHUB_TOKEN) or wait until the rate limit resets."));

        mockMvc.perform(get("/api/github/limited/score"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ── Organization analytics ──

    @Test
    void orgOverviewEndpointReturnsTeamAnalytics() throws Exception {
        when(organizationAnalyticsService.getOverview("vercel")).thenReturn(orgOverview());

        mockMvc.perform(get("/api/github/org/vercel/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.login").value("vercel"))
                .andExpect(jsonPath("$.data.totalRepos").value(42))
                .andExpect(jsonPath("$.data.activeRepos").value(18))
                .andExpect(jsonPath("$.data.teamActivity.commits30d").value(184))
                .andExpect(jsonPath("$.data.teamActivity.issues90d").value(96))
                .andExpect(jsonPath("$.data.topContributors[0].contributionPercent").value(28.4));
    }

    // ── AI endpoints ──

    @Test
    void aiStatusReflectsEnabledFlag() throws Exception {
        when(geminiService.isEnabled()).thenReturn(false);

        mockMvc.perform(get("/api/ai/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.provider").value("Google Gemini"));
    }

    @Test
    void aiSummaryRunsRealScoreFlowAndReturnsGeneratedText() throws Exception {
        stubDeveloper("ai-dev");
        when(geminiService.generateDeveloperSummary(anyString(), any(), any(), anyList()))
                .thenReturn("Alice is a strong backend engineer with solid Java fundamentals.");

        mockMvc.perform(get("/api/ai/summary/ai-dev"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("Alice is a strong backend engineer with solid Java fundamentals."));
    }

    @Test
    void repositoryReviewReturnsNotFoundForMissingRepo() throws Exception {
        stubDeveloper("norepo");
        when(gitHubService.getRepositories("norepo")).thenReturn(List.of(repo("real", "Java", 5, 100)));

        mockMvc.perform(get("/api/ai/review/norepo/does-not-exist"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ── Reports (authenticated; real ScoringEngine + JPA persistence) ──

    @Test
    void reportsEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/reports/all"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        // The public analysis surface stays open without a token.
        stubDeveloper("anon");
        mockMvc.perform(get("/api/github/anon/score"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void reportsRecordGenerateAndHistoryPersistScores() throws Exception {
        stubDeveloper("report-dev");

        mockMvc.perform(post("/api/reports/record/report-dev")
                        .header("Authorization", "Bearer " + validToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value("report-dev"))
                .andExpect(jsonPath("$.data.overallScore").isNumber());

        mockMvc.perform(get("/api/reports/history/report-dev")
                        .header("Authorization", "Bearer " + validToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.data[0].username").value("report-dev"));

        mockMvc.perform(get("/api/reports/generate/report-dev")
                        .header("Authorization", "Bearer " + validToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.score.overallScore").isNumber())
                .andExpect(jsonPath("$.data.profile.username").value("report-dev"))
                .andExpect(jsonPath("$.data.history", org.hamcrest.Matchers.hasSize(2)));

        // Two snapshots persisted (record + generate), ordered ascending.
        List<ScoreHistory> saved = scoreHistoryRepository.findAll();
        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).getUsername()).isEqualTo("report-dev");
        assertThat(saved.get(0).getOverallScore()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void latestScoreReturnsNotFoundFlagWhenNoHistory() throws Exception {
        mockMvc.perform(get("/api/reports/latest/nobody")
                        .header("Authorization", "Bearer " + validToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));
    }
}
