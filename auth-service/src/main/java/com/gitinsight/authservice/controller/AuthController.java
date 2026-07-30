package com.gitinsight.authservice.controller;

import com.gitinsight.authservice.dto.*;
import com.gitinsight.authservice.service.AuthService;
import com.gitinsight.common.dto.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
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

    @GetMapping("/oauth/github")
    public ResponseEntity<ApiResponse<String>> githubOAuth(@RequestParam(defaultValue = "http://localhost:5173") String redirectUri) {
        String githubAuthUrl = "https://github.com/login/oauth/authorize" +
                "?client_id=" + System.getenv().getOrDefault("GITHUB_CLIENT_ID", "mock-client-id") +
                "&redirect_uri=http://localhost:8083/api/auth/oauth/github/callback" +
                "&scope=user:email,read:user" +
                "&state=" + redirectUri;
        return ResponseEntity.ok(new ApiResponse<>(true, "Redirect to GitHub", githubAuthUrl));
    }

    @GetMapping("/oauth/github/callback")
    public ResponseEntity<ApiResponse<AuthResponse>> githubCallback(@RequestParam String code,
                                                                      @RequestParam(defaultValue = "http://localhost:5173") String state) {
        try {
            AuthResponse response = authService.handleGithubOAuth(code);
            return ResponseEntity.ok(new ApiResponse<>(true, "GitHub OAuth successful", response));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(false, e.getMessage(), null));
        }
    }
}
