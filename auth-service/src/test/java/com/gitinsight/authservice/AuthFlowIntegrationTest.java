package com.gitinsight.authservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gitinsight.authservice.entity.User;
import com.gitinsight.authservice.repository.RecruiterNoteRepository;
import com.gitinsight.authservice.repository.SavedCandidateRepository;
import com.gitinsight.authservice.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for the full auth flow through the real HTTP + security + JPA stack
 * (H2 in PostgreSQL mode): register → login → JWT → /me → refresh → role authorization →
 * recruiter module (save candidates, notes, bookmarks, stats).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowIntegrationTest {

    private static final String PASSWORD = "sup3r-secret";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SavedCandidateRepository savedCandidateRepository;

    @Autowired
    private RecruiterNoteRepository recruiterNoteRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @AfterEach
    void cleanDb() {
        // Children (saved_candidates / recruiter_notes) reference users via FK — delete first.
        recruiterNoteRepository.deleteAll();
        savedCandidateRepository.deleteAll();
        userRepository.deleteAll();
    }

    @BeforeEach
    void resetGitHubMock() {
        StubGitHubConfig.github.reset();
    }

    // ── Helpers ──

    private String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    private JsonNode register(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Alice Dev\",\"email\":\"" + email
                                + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private String loginToken(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("token").asText();
    }

    private User createUser(String email, User.Role role) {
        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(PASSWORD));
        user.setName("Test " + role);
        user.setRole(role);
        user.setEnabled(true);
        return userRepository.save(user);
    }

    // ── Registration ──

    @Test
    void registerReturnsTokensAndPersistsUser() throws Exception {
        String email = uniqueEmail();
        JsonNode data = register(email, PASSWORD);

        org.assertj.core.api.Assertions.assertThat(data.path("token").asText()).isNotBlank();
        org.assertj.core.api.Assertions.assertThat(data.path("refreshToken").asText()).isNotBlank();
        org.assertj.core.api.Assertions.assertThat(data.path("user").path("email").asText()).isEqualTo(email);
        org.assertj.core.api.Assertions.assertThat(data.path("user").path("role").asText()).isEqualTo("USER");
        org.assertj.core.api.Assertions.assertThat(userRepository.findByEmail(email)).isPresent();
    }

    @Test
    void duplicateEmailRejected() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Clone\",\"email\":\"" + email
                                + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void invalidRegisterBodyFailsValidation() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"email\":\"not-an-email\",\"password\":\"123\"}"))
                .andExpect(status().isBadRequest());
    }

    // ── Login ──

    @Test
    void loginReturnsTokensForValidCredentials() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.email").value(email));
    }

    @Test
    void loginWithWrongPasswordRejected() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ── /me + token enforcement ──

    @Test
    void meRequiresBearerToken() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());

        String email = uniqueEmail();
        register(email, PASSWORD);
        String token = loginToken(email, PASSWORD);

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.role").value("USER"));
    }

    // ── Refresh ──

    @Test
    void refreshExchangesRefreshTokenForNewTokens() throws Exception {
        String email = uniqueEmail();
        JsonNode data = register(email, PASSWORD);
        String refreshToken = data.path("refreshToken").asText();

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.email").value(email));
    }

    @Test
    void refreshWithInvalidTokenRejected() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"not-a-real-token\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ── Role authorization ──

    @Test
    void recruiterEndpointsRejectUserRole() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD);
        String token = loginToken(email, PASSWORD);

        mockMvc.perform(get("/api/recruiter/stats")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void recruiterFlowSaveNoteBookmarkStats() throws Exception {
        String email = uniqueEmail();
        createUser(email, User.Role.RECRUITER);
        String token = loginToken(email, PASSWORD);

        // Save a candidate
        mockMvc.perform(post("/api/recruiter/candidates/save")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"octocat\",\"name\":\"Octo Cat\",\"score\":87}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.candidateUsername").value("octocat"))
                .andExpect(jsonPath("$.data.recruiter.password").doesNotExist());

        // List saved candidates (recruiter identity must NOT leak the password hash)
        mockMvc.perform(get("/api/recruiter/candidates")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].candidateUsername").value("octocat"))
                .andExpect(jsonPath("$.data[0].recruiter.password").doesNotExist());

        // Add + list a note
        mockMvc.perform(post("/api/recruiter/candidates/octocat/notes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Strong backend\",\"content\":\"Good Spring experience.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/recruiter/candidates/octocat/notes")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].title").value("Strong backend"));

        // Toggle bookmark off
        mockMvc.perform(put("/api/recruiter/candidates/octocat/bookmark")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookmarked\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bookmarked").value(false));

        // Stats reflect everything
        mockMvc.perform(get("/api/recruiter/stats")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.savedCandidates").value(1))
                .andExpect(jsonPath("$.data.totalNotes").value(1));

        // Remove the candidate
        mockMvc.perform(delete("/api/recruiter/candidates/octocat")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/recruiter/candidates")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    // ── OAuth entry point (public) ──

    @Test
    void githubOAuthEntryPointRedirectsToGitHubWithRandomState() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/oauth/github"))
                .andExpect(status().isFound())
                .andReturn();
        String location = result.getResponse().getHeader("Location");
        org.assertj.core.api.Assertions.assertThat(location)
                .isNotNull()
                .startsWith("https://github.com/login/oauth/authorize")
                .contains("client_id=test-client-id")
                .contains("redirect_uri=")
                .contains("state=");
        // The state must be a random token — never the frontend redirect URL itself.
        org.assertj.core.api.Assertions.assertThat(location).doesNotContain("localhost:5173");
    }

    @Test
    void githubOAuthCallbackRejectsUnknownState() throws Exception {
        mockMvc.perform(get("/api/auth/oauth/github/callback")
                        .param("code", "abc123")
                        .param("state", "forged-state"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ── Full OAuth round trip (GitHub's token + user endpoints stubbed) ──

    @Test
    void githubOAuthFullFlowExchangesCodeUpsertsUserAndRedirectsWithTokens() throws Exception {
        // GitHub: exchange the one-time code for an access token.
        StubGitHubConfig.github.expect(requestTo("https://github.com/login/oauth/access_token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE))
                .andRespond(withSuccess(
                        "{\"access_token\":\"gho_test-token-123\",\"token_type\":\"bearer\",\"scope\":\"user:email,read:user\"}",
                        MediaType.APPLICATION_JSON));

        // GitHub: fetch the authenticated user's profile.
        StubGitHubConfig.github.expect(requestTo("https://api.github.com/user"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer gho_test-token-123"))
                .andRespond(withSuccess(
                        "{\"id\":999,\"login\":\"octocat\",\"name\":\"Octo Cat\",\"email\":\"octo@example.com\",\"avatar_url\":\"https://avatars.example/u.png\"}",
                        MediaType.APPLICATION_JSON));

        // 1. Entry point: 302 to GitHub with a random state.
        MvcResult entry = mockMvc.perform(get("/api/auth/oauth/github"))
                .andExpect(status().isFound())
                .andReturn();
        String authorizeUrl = entry.getResponse().getHeader("Location");
        org.assertj.core.api.Assertions.assertThat(authorizeUrl).isNotNull();
        String state = UriComponentsBuilder.fromUriString(authorizeUrl)
                .build().getQueryParams().getFirst("state");
        org.assertj.core.api.Assertions.assertThat(state).isNotBlank();

        // 2. Callback with the valid state: code exchanged, user upserted, browser
        //    redirected back to the frontend with fresh JWTs.
        MvcResult callback = mockMvc.perform(get("/api/auth/oauth/github/callback")
                        .param("code", "one-time-code")
                        .param("state", state))
                .andExpect(status().isFound())
                .andReturn();
        String redirect = callback.getResponse().getHeader("Location");
        org.assertj.core.api.Assertions.assertThat(redirect)
                .isNotNull()
                .startsWith("http://localhost:5173/auth/callback?")
                .contains("token=")
                .contains("refreshToken=");

        // 3. The GitHub profile was upserted into the local account store.
        User saved = userRepository.findByGithubId(999L).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(saved.getGithubUsername()).isEqualTo("octocat");
        org.assertj.core.api.Assertions.assertThat(saved.getEmail()).isEqualTo("octo@example.com");
        org.assertj.core.api.Assertions.assertThat(saved.getName()).isEqualTo("Octo Cat");
        org.assertj.core.api.Assertions.assertThat(saved.getRole()).isEqualTo(User.Role.USER);

        // 4. The state is single-use: replaying the same callback is rejected.
        mockMvc.perform(get("/api/auth/oauth/github/callback")
                        .param("code", "one-time-code")
                        .param("state", state))
                .andExpect(status().isBadRequest());
    }

    /**
     * Replaces the auto-configured {@link RestClient.Builder} with one bound to a
     * {@link MockRestServiceServer}, so the OAuth flow's calls to GitHub's token
     * and user endpoints can be stubbed while every other layer runs for real.
     */
    @TestConfiguration
    static class StubGitHubConfig {

        static MockRestServiceServer github;

        @Bean
        @Primary
        RestClient.Builder stubGitHubRestClientBuilder() {
            RestClient.Builder builder = RestClient.builder();
            github = MockRestServiceServer.bindTo(builder).build();
            return builder;
        }
    }
}
