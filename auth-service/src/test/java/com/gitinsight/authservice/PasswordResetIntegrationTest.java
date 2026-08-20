package com.gitinsight.authservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gitinsight.authservice.entity.PasswordResetToken;
import com.gitinsight.authservice.entity.User;
import com.gitinsight.authservice.repository.PasswordResetTokenRepository;
import com.gitinsight.authservice.repository.RecruiterNoteRepository;
import com.gitinsight.authservice.repository.SavedCandidateRepository;
import com.gitinsight.authservice.repository.UserRepository;
import com.gitinsight.authservice.security.FixedWindowRateLimiter;
import com.gitinsight.authservice.security.InMemoryFixedWindowRateLimiter;
import com.gitinsight.authservice.security.InMemoryOAuthStateStore;
import com.gitinsight.authservice.security.OAuthStateStore;
import com.gitinsight.authservice.service.PasswordResetEmailService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the forgot-password and reset-password flow.
 * Uses H2 in-memory database with the real Spring Security + JPA stack.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PasswordResetIntegrationTest {

    private static final String PASSWORD = "Sup3r-secret";
    private static final String NEW_PASSWORD = "N3w-securepwd";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Autowired
    private SavedCandidateRepository savedCandidateRepository;

    @Autowired
    private RecruiterNoteRepository recruiterNoteRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private PasswordResetEmailService emailService;

    @AfterEach
    void cleanDb() {
        recruiterNoteRepository.deleteAll();
        savedCandidateRepository.deleteAll();
        tokenRepository.deleteAll();
        userRepository.deleteAll();
        reset(emailService);
    }

    @BeforeEach
    void resetGitHubMock() {
        StubGitHubConfig.github.reset();
    }

    // ── Helpers ──

    private String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    private User createUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(PASSWORD));
        user.setName("Test User");
        user.setRole(User.Role.USER);
        user.setEnabled(true);
        return userRepository.save(user);
    }

    private User createDisabledUser(String email) {
        User user = createUser(email);
        user.setEnabled(false);
        return userRepository.save(user);
    }

    // ═══════════════════════════════════════════════════════
    // 1. FORGOT PASSWORD TESTS
    // ═══════════════════════════════════════════════════════

    @Test
    void existingEmailReturnsGenericSuccess() throws Exception {
        String email = uniqueEmail();
        createUser(email);

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType("application/json")
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("If the account exists, a password reset link has been sent."));

        ArgumentCaptor<String> emailCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendResetEmail(emailCaptor.capture(), any(), tokenCaptor.capture());
        assertThat(emailCaptor.getValue()).isEqualTo(email);
        assertThat(tokenCaptor.getValue()).isNotBlank();
    }

    @Test
    void unknownEmailReturnsIdenticalGenericSuccess() throws Exception {
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType("application/json")
                        .content("{\"email\":\"nobody@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("If the account exists, a password reset link has been sent."));

        verify(emailService, never()).sendResetEmail(any(), any(), any());
    }

    @Test
    void existingUserGetsResetToken() throws Exception {
        String email = uniqueEmail();
        createUser(email);

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType("application/json")
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk());

        User user = userRepository.findByEmail(email).orElseThrow();
        List<PasswordResetToken> tokens = tokenRepository.findAll();
        assertThat(tokens).hasSize(1);
        assertThat(tokens.get(0).getUser().getId()).isEqualTo(user.getId());
        assertThat(tokens.get(0).isUsed()).isFalse();
        assertThat(tokens.get(0).getExpiresAt()).isAfter(LocalDateTime.now());
    }

    @Test
    void rawTokenIsNeverStored() throws Exception {
        String email = uniqueEmail();
        createUser(email);

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType("application/json")
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<String> rawTokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendResetEmail(any(), any(), rawTokenCaptor.capture());
        String rawToken = rawTokenCaptor.getValue();

        List<PasswordResetToken> tokens = tokenRepository.findAll();
        assertThat(tokens).hasSize(1);
        assertThat(tokens.get(0).getTokenHash()).isNotEqualTo(rawToken);
        assertThat(tokens.get(0).getTokenHash()).hasSizeGreaterThan(20);
    }

    @Test
    void previousResetTokenInvalidatedAfterNewRequest() throws Exception {
        String email = uniqueEmail();
        createUser(email);

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType("application/json")
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk());

        List<PasswordResetToken> tokens1 = tokenRepository.findAll();
        assertThat(tokens1).hasSize(1);

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType("application/json")
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk());

        List<PasswordResetToken> tokens2 = tokenRepository.findAll();
        assertThat(tokens2).hasSize(1);
        assertThat(tokens2.get(0).getTokenHash()).isNotEqualTo(tokens1.get(0).getTokenHash());
    }

    @Test
    void forgotPasswordEndpointWorksWithoutJwt() throws Exception {
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType("application/json")
                        .content("{\"email\":\"test@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ═══════════════════════════════════════════════════════
    // 2. RESET PASSWORD TESTS
    // ═══════════════════════════════════════════════════════

    @Test
    void correctTokenResetsPassword() throws Exception {
        String email = uniqueEmail();
        createUser(email);

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType("application/json")
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendResetEmail(any(), any(), tokenCaptor.capture());
        String rawToken = tokenCaptor.getValue();

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType("application/json")
                        .content("{\"token\":\"" + rawToken + "\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Password reset successful. Please log in again."));

        // Old password no longer works.
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());

        // New password works.
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void invalidTokenRejected() throws Exception {
        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType("application/json")
                        .content("{\"token\":\"completely-fake-token\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid or expired reset link."));
    }

    @Test
    void usedTokenRejected() throws Exception {
        String email = uniqueEmail();
        createUser(email);

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType("application/json")
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendResetEmail(any(), any(), tokenCaptor.capture());
        String rawToken = tokenCaptor.getValue();

        // Use the token once — should succeed.
        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType("application/json")
                        .content("{\"token\":\"" + rawToken + "\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isOk());

        // Use the same token again — should fail.
        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType("application/json")
                        .content("{\"token\":\"" + rawToken + "\",\"newPassword\":\"Another1Pass\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid or expired reset link."));
    }

    @Test
    void expiredTokenRejected() throws Exception {
        String email = uniqueEmail();
        User user = createUser(email);

        PasswordResetToken expired = new PasswordResetToken();
        expired.setUser(user);
        expired.setTokenHash("expired-hash");
        expired.setExpiresAt(LocalDateTime.now().minusHours(1));
        expired.setUsed(false);
        tokenRepository.save(expired);

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType("application/json")
                        .content("{\"token\":\"expired-hash\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid or expired reset link."));
    }

    @Test
    void disabledUserCannotReset() throws Exception {
        String email = uniqueEmail();
        createDisabledUser(email);

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType("application/json")
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(emailService, never()).sendResetEmail(any(), any(), any());
    }

    @Test
    void newPasswordIsBcryptEncoded() throws Exception {
        String email = uniqueEmail();
        createUser(email);

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType("application/json")
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendResetEmail(any(), any(), tokenCaptor.capture());

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType("application/json")
                        .content("{\"token\":\"" + tokenCaptor.getValue() + "\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isOk());

        User user = userRepository.findByEmail(email).orElseThrow();
        assertThat(user.getPassword()).startsWith("$2");
        assertThat(passwordEncoder.matches(NEW_PASSWORD, user.getPassword())).isTrue();
    }

    @Test
    void resetPasswordEndpointWorksWithoutJwt() throws Exception {
        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType("application/json")
                        .content("{\"token\":\"fake-token\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
        // Important: we got 400 (invalid token), NOT 401/403 (auth required).
    }

    @Test
    void emailFailureDoesNotLeakInternalDetails() throws Exception {
        String email = uniqueEmail();
        createUser(email);

        doThrow(new RuntimeException("SMTP connection refused: javax.mail..."))
                .when(emailService).sendResetEmail(any(), any(), any());

        String response = mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType("application/json")
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain("SMTP");
        assertThat(response).doesNotContain("javax.mail");
        assertThat(response).doesNotContain("connection refused");
    }

    /**
     * Replaces the auto-configured {@link RestClient.Builder} with one bound to a
     * {@link MockRestServiceServer}, and provides in-memory rate limiter + OAuth state
     * for tests that don't need Redis.
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

        @Bean
        @Primary
        FixedWindowRateLimiter fixedWindowRateLimiter() {
            return new InMemoryFixedWindowRateLimiter(60_000L);
        }

        @Bean
        @Primary
        OAuthStateStore oauthStateStore() {
            return new InMemoryOAuthStateStore();
        }
    }
}
