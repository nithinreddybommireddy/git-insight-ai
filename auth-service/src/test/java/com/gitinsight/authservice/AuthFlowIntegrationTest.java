package com.gitinsight.authservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gitinsight.authservice.entity.User;
import com.gitinsight.authservice.repository.RecruiterNoteRepository;
import com.gitinsight.authservice.repository.SavedCandidateRepository;
import com.gitinsight.authservice.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
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
    void githubOAuthEntryPointReturnsAuthorizeUrl() throws Exception {
        mockMvc.perform(get("/api/auth/oauth/github"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.containsString("github.com/login/oauth/authorize")));
    }
}
