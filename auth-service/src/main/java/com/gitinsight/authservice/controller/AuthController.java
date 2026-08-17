package com.gitinsight.authservice.controller;

import com.gitinsight.authservice.config.AuthCookieService;
import com.gitinsight.authservice.config.OAuthProperties;
import com.gitinsight.authservice.dto.*;
import com.gitinsight.authservice.security.OAuthStateStore;
import com.gitinsight.authservice.service.AuthService;
import com.gitinsight.common.dto.response.ApiResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final OAuthProperties oauthProperties;
    private final OAuthStateStore oauthStateStore;
    private final AuthCookieService authCookieService;
    private final long accessMaxAgeSeconds;
    private final long refreshMaxAgeSeconds;

    public AuthController(AuthService authService,
                          OAuthProperties oauthProperties,
                          OAuthStateStore oauthStateStore,
                          AuthCookieService authCookieService,
                          @Value("${app.jwt.expiration-ms}") long accessExpirationMs,
                          @Value("${app.jwt.refresh-expiration-ms}") long refreshExpirationMs) {
        this.authService = authService;
        this.oauthProperties = oauthProperties;
        this.oauthStateStore = oauthStateStore;
        this.authCookieService = authCookieService;
        this.accessMaxAgeSeconds = accessExpirationMs / 1000;
        this.refreshMaxAgeSeconds = refreshExpirationMs / 1000;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        try {
            AuthResponse response = authService.register(request);
            return withSessionCookies(ResponseEntity.ok(new ApiResponse<>(true, "Registration successful", response)), response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(false, e.getMessage(), null));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        try {
            AuthResponse response = authService.login(request);
            return withSessionCookies(ResponseEntity.ok(new ApiResponse<>(true, "Login successful", response)), response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(401)
                    .body(new ApiResponse<>(false, e.getMessage(), null));
        }
    }

    /**
     * Exchange a refresh token for a fresh session. The token is read from the
     * HttpOnly refresh cookie first (browser flow); API clients may still send
     * it in the JSON body.
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@RequestBody(required = false) RefreshTokenRequest request,
                                                             HttpServletRequest httpRequest) {
        String refreshToken = refreshCookie(httpRequest);
        if (refreshToken == null && request != null && request.getRefreshToken() != null) {
            refreshToken = request.getRefreshToken();
        }
        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.status(401)
                    .body(new ApiResponse<>(false, "Missing refresh token", null));
        }

        try {
            AuthResponse response = authService.refresh(refreshToken);
            return withSessionCookies(ResponseEntity.ok(new ApiResponse<>(true, "Token refreshed successfully", response)), response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(401)
                    .body(new ApiResponse<>(false, e.getMessage(), null));
        }
    }

    /** Clears the HttpOnly session cookies. No authentication required — clearing an absent session is a no-op. */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout() {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, authCookieService.clearSessionCookies())
                .body(new ApiResponse<>(true, "Logged out", null));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> me(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401)
                    .body(new ApiResponse<>(false, "Not authenticated", null));
        }
        Long userId = (Long) authentication.getPrincipal();
        try {
            UserResponse user = authService.getMe(userId);
            return ResponseEntity.ok(new ApiResponse<>(true, "User fetched successfully", user));
        } catch (RuntimeException e) {
            // Covers disabled accounts: the session must not keep working after
            // an admin disables the user, so /me treats it as unauthenticated.
            return ResponseEntity.status(401)
                    .body(new ApiResponse<>(false, "Not authenticated", null));
        }
    }

    /**
     * GitHub OAuth entry point. Generates a random state (stored server-side
     * against the configured post-login destination), then redirects the browser
     * to GitHub's authorize page. The configured client credentials come from
     * the environment, never from source code.
     *
     * <p>The post-login destination is ALWAYS the server-configured
     * {@code OAUTH_FRONTEND_REDIRECT_URI} — never a caller-supplied URL.
     */
    @GetMapping("/oauth/github")
    public ResponseEntity<ApiResponse<Void>> githubOAuth() {
        if (!oauthProperties.isConfigured()) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(false,
                    "GitHub OAuth is not configured. Set GITHUB_CLIENT_ID and GITHUB_CLIENT_SECRET environment variables.",
                    null));
        }

        String target = oauthProperties.getFrontendRedirectUri();
        String state = oauthStateStore.create(target);
        if (state == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ApiResponse<>(false,
                    "OAuth is temporarily unavailable. Please try again in a moment.", null));
        }
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
     * for tokens, sets the HttpOnly session cookies, and redirects the browser
     * to the frontend WITHOUT any tokens in the URL — the frontend's
     * /auth/callback route simply confirms the session via {@code /me}. Failures
     * redirect back with an {@code error} query parameter.
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
            // Tokens ride in HttpOnly cookies; the redirect URL stays clean.
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(target))
                    .header(HttpHeaders.SET_COOKIE, authCookieService.sessionCookies(response, accessMaxAgeSeconds, refreshMaxAgeSeconds))
                    .build();
        } catch (RuntimeException e) {
            String location = appendQuery(target, "error=" + urlEncode(e.getMessage()));
            return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(location)).build();
        }
    }

    private ResponseEntity<ApiResponse<AuthResponse>> withSessionCookies(
            ResponseEntity<ApiResponse<AuthResponse>> response, AuthResponse auth) {
        return ResponseEntity.status(response.getStatusCode())
                .header(HttpHeaders.SET_COOKIE, authCookieService.sessionCookies(auth, accessMaxAgeSeconds, refreshMaxAgeSeconds))
                .body(response.getBody());
    }

    private static String refreshCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        return Arrays.stream(cookies)
                .filter(c -> com.gitinsight.common.security.AuthCookieNames.REFRESH.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    private static String appendQuery(String url, String query) {
        return url + (url.contains("?") ? "&" : "?") + query;
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
