package com.gitinsight.authservice.service;

import com.gitinsight.authservice.config.OAuthProperties;
import com.gitinsight.authservice.dto.*;
import com.gitinsight.authservice.entity.User;
import com.gitinsight.authservice.repository.UserRepository;
import com.gitinsight.common.security.JwtUtil;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final OAuthProperties oauthProperties;
    private final RestClient restClient;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil,
                       OAuthProperties oauthProperties,
                       RestClient.Builder restClientBuilder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.oauthProperties = oauthProperties;
        this.restClient = restClientBuilder.build();
    }

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already registered");
        }

        User user = new User();
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setName(request.getName());
        user.setRole(User.Role.USER);
        user.setEnabled(true);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        user = userRepository.save(user);

        return buildAuthResponse(user);
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid email or password");
        }

        if (!user.isEnabled()) {
            throw new RuntimeException("Account is disabled");
        }

        return buildAuthResponse(user);
    }

    public AuthResponse refresh(String refreshToken) {
        if (!jwtUtil.validateToken(refreshToken)) {
            throw new RuntimeException("Invalid or expired refresh token");
        }

        Long userId = jwtUtil.getUserIdFromToken(refreshToken);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return buildAuthResponse(user);
    }

    public UserResponse getMe(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return mapToUserResponse(user);
    }

    /**
     * Complete the GitHub OAuth code exchange: trade the one-time authorization
     * code for an access token, fetch the user's GitHub profile, upsert the local
     * account (linking an existing account by GitHub id or email), and mint tokens.
     */
    public AuthResponse handleGithubOAuth(String code, String redirectUri) {
        if (!oauthProperties.isConfigured()) {
            throw new RuntimeException(
                    "GitHub OAuth is not configured. Set GITHUB_CLIENT_ID and GITHUB_CLIENT_SECRET environment variables.");
        }

        String accessToken = exchangeCodeForToken(code, redirectUri);
        GitHubUserResponse githubUser = fetchGitHubUser(accessToken);
        User user = upsertGithubUser(githubUser);
        return buildAuthResponse(user);
    }

    private String exchangeCodeForToken(String code, String redirectUri) {
        String body = "client_id=" + urlEncode(oauthProperties.getClientId())
                + "&client_secret=" + urlEncode(oauthProperties.getClientSecret())
                + "&code=" + urlEncode(code)
                + "&redirect_uri=" + urlEncode(redirectUri);

        AccessTokenResponse response = restClient.post()
                .uri("https://github.com/login/oauth/access_token")
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(body)
                .retrieve()
                .body(AccessTokenResponse.class);

        if (response == null || response.access_token() == null || response.access_token().isBlank()) {
            throw new RuntimeException("GitHub OAuth failed: could not exchange the authorization code for an access token.");
        }
        return response.access_token();
    }

    private GitHubUserResponse fetchGitHubUser(String accessToken) {
        GitHubUserResponse user = restClient.get()
                .uri("https://api.github.com/user")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .header("X-GitHub-Api-Version", "2022-11-28")
                .retrieve()
                .body(GitHubUserResponse.class);

        if (user == null || user.id() == null) {
            throw new RuntimeException("GitHub OAuth failed: could not fetch the GitHub profile for the authenticated user.");
        }
        return user;
    }

    private User upsertGithubUser(GitHubUserResponse githubUser) {
        Optional<User> byGithubId = userRepository.findByGithubId(githubUser.id());
        Optional<User> byEmail = (githubUser.email() == null || githubUser.email().isBlank())
                ? Optional.empty()
                : userRepository.findByEmail(githubUser.email());

        User user = byGithubId.or(() -> byEmail).orElse(null);
        boolean isNew = user == null;
        if (isNew) {
            user = new User();
            // Random unrecoverable password: the account can only be used via GitHub OAuth.
            user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
            user.setRole(User.Role.USER);
            user.setEnabled(true);
        }

        user.setGithubId(githubUser.id());
        user.setGithubUsername(githubUser.login());
        if (isNew) {
            user.setEmail(githubUser.email() != null && !githubUser.email().isBlank()
                    ? githubUser.email()
                    : githubUser.login() + "@users.noreply.github.com");
            user.setName(githubUser.name() != null && !githubUser.name().isBlank()
                    ? githubUser.name()
                    : githubUser.login());
        }
        if (githubUser.avatar_url() != null && !githubUser.avatar_url().isBlank()) {
            user.setAvatarUrl(githubUser.avatar_url());
        }

        return userRepository.save(user);
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /** GitHub access-token exchange response (snake_case mapped directly). */
    private record AccessTokenResponse(String access_token, String token_type, String scope) {
    }

    /** GitHub /user response projection (snake_case mapped directly). */
    private record GitHubUserResponse(Long id, String login, String name, String email, String avatar_url) {
    }

    private AuthResponse buildAuthResponse(User user) {
        String token = jwtUtil.generateToken(user.getId(), user.getEmail(), user.getRole().name());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId());

        return new AuthResponse(token, refreshToken, mapToUserResponse(user));
    }

    private UserResponse mapToUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .avatarUrl(user.getAvatarUrl())
                .role(user.getRole().name())
                .githubUsername(user.getGithubUsername())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
