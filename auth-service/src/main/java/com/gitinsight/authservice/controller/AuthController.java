package com.gitinsight.authservice.controller;

import com.gitinsight.authservice.config.OAuthProperties;
import com.gitinsight.authservice.dto.*;
import com.gitinsight.authservice.security.OAuthStateStore;
import com.gitinsight.authservice.service.AuthService;
import com.gitinsight.common.dto.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final OAuthProperties oauthProperties;
    private final OAuthStateStore oauthStateStore;

    public AuthController(AuthService authService,
                          OAuthProperties oauthProperties,
                          OAuthStateStore oauthStateStore) {
        this.authService = authService;
        this.oauthProperties = oauthProperties;
        this.oauthStateStore = oauthStateStore;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        try {
            AuthResponse response = authService.register(request);
            return ResponseEntity.ok(new ApiResponse<>(true, "Registration successful", response));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(false, e.getMessage(), null));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        try {
            AuthResponse response = authService.login(request);
            return ResponseEntity.ok(new ApiResponse<>(true, "Login successful", response));
        } catch (RuntimeException e) {
            return ResponseEntity.status(401)
                    .body(new ApiResponse<>(false, e.getMessage(), null));
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        try {
            AuthResponse response = authService.refresh(request.getRefreshToken());
            return ResponseEntity.ok(new ApiResponse<>(true, "Token refreshed successfully", response));
        } catch (RuntimeException e) {
            return ResponseEntity.status(401)
                    .body(new ApiResponse<>(false, e.getMessage(), null));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> me(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401)
                    .body(new ApiResponse<>(false, "Not authenticated", null));
        }
        Long userId = (Long) authentication.getPrincipal();
        UserResponse user = authService.getMe(userId);
        return ResponseEntity.ok(new ApiResponse<>(true, "User fetched successfully", user));
    }

    /**
     * GitHub OAuth entry point. Generates a random state (stored server-side
     * against the desired post-login destination), then redirects the browser
     * to GitHub's authorize page. The configured client credentials come from
     * the environment, never from source code.
     */
    @GetMapping("/oauth/github")
    public ResponseEntity<ApiResponse<Void>> githubOAuth(@RequestParam(required = false) String redirectUri) {
        if (!oauthProperties.isConfigured()) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(false,
                    "GitHub OAuth is not configured. Set GITHUB_CLIENT_ID and GITHUB_CLIENT_SECRET environment variables.",
                    null));
        }

        String target = (redirectUri == null || redirectUri.isBlank())
                ? oauthProperties.getFrontendRedirectUri()
                : redirectUri;
        if (!isHttpUrl(target)) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(false,
                    "redirectUri must be an http(s) URL.", null));
        }

        String state = oauthStateStore.create(target);
        String authorizeUrl = "https://github.com/login/oauth/authorize"
                + "?client_id=" + urlEncode(oauthProperties.getClientId())
                + "&redirect_uri=" + urlEncode(oauthProperties.getRedirectUri())
                + "&scope=user:email,read:user"
                + "&state=" + urlEncode(state);

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(authorizeUrl))
                .build();
    }

    /**
     * GitHub OAuth callback. Validates the single-use state, exchanges the code
     * for tokens, and redirects the browser back to the frontend with the JWT
     * (consumed by the frontend's /auth/callback route). Failures also redirect
     * back with an {@code error} query parameter.
     */
    @GetMapping("/oauth/github/callback")
    public ResponseEntity<ApiResponse<Void>> githubCallback(@RequestParam(required = false) String code,
                                                              @RequestParam(required = false) String state) {
        String target = oauthStateStore.consume(state);
        if (target == null) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(false,
                    "Invalid or expired OAuth state. Please try signing in again.", null));
        }
        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(false,
                    "GitHub did not return an authorization code.", null));
        }

        try {
            AuthResponse response = authService.handleGithubOAuth(code, oauthProperties.getRedirectUri());
            String location = appendQuery(target,
                    "token=" + urlEncode(response.getToken())
                            + "&refreshToken=" + urlEncode(response.getRefreshToken()));
            return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(location)).build();
        } catch (RuntimeException e) {
            String location = appendQuery(target, "error=" + urlEncode(e.getMessage()));
            return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(location)).build();
        }
    }

    private static boolean isHttpUrl(String value) {
        return value.startsWith("http://") || value.startsWith("https://");
    }

    private static String appendQuery(String url, String query) {
        return url + (url.contains("?") ? "&" : "?") + query;
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
