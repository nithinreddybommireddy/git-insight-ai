package com.gitinsight.githubservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gitinsight.common.dto.response.ApiResponse;
import com.gitinsight.common.security.CsrfCookieFilter;
import com.gitinsight.common.security.JwtAuthFilter;
import com.gitinsight.githubservice.security.AiRateLimitFilter;
import com.gitinsight.githubservice.security.GitHubRateLimitFilter;
import com.gitinsight.githubservice.security.InternalApiKeyFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/**
 * Security configuration for github-service.
 *
 * <p>Public GitHub analysis endpoints remain accessible without authentication.
 *
 * <p>AI endpoints are rate-limited because they may consume Gemini/API quota.
 *
 * <p>Report endpoints require a valid JWT.
 *
 * <p>The internal job-match endpoint is protected by InternalApiKeyFilter.
 *
 * <p>CSRF uses the same XSRF-TOKEN / X-XSRF-TOKEN names as auth-service so
 * the frontend's single axios instance can read the cookie and send the header
 * for all requests regardless of which backend handles them.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final AiRateLimitFilter aiRateLimitFilter;
    private final GitHubRateLimitFilter gitHubRateLimitFilter;
    private final InternalApiKeyFilter internalApiKeyFilter;
    private final ObjectMapper objectMapper;

    public SecurityConfig(
            JwtAuthFilter jwtAuthFilter,
            AiRateLimitFilter aiRateLimitFilter,
            GitHubRateLimitFilter gitHubRateLimitFilter,
            InternalApiKeyFilter internalApiKeyFilter,
            ObjectMapper objectMapper
    ) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.aiRateLimitFilter = aiRateLimitFilter;
        this.gitHubRateLimitFilter = gitHubRateLimitFilter;
        this.internalApiKeyFilter = internalApiKeyFilter;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                /*
                 * CSRF
                 *
                 * GitHub analysis endpoints are public GET endpoints.
                 * Job-match is a server-to-server endpoint protected by
                 * InternalApiKeyFilter.
                 *
                 * Other state-changing endpoints remain protected by CSRF.
                 *
                 * Uses the same XSRF-TOKEN / X-XSRF-TOKEN names as
                 * auth-service so a single frontend axios instance works.
                 */
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(
                                new CsrfTokenRequestAttributeHandler()
                        )
                        .ignoringRequestMatchers(
                                "/api/github/**",
                                "/api/ai/job-match",
                                "/api/health",
                                "/actuator/health"
                        )
                )

                /*
                 * CORS
                 */
                .cors(Customizer.withDefaults())

                /*
                 * Stateless API
                 */
                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                /*
                 * Authorization
                 */
                .authorizeHttpRequests(auth -> auth

                        // Health endpoints
                        .requestMatchers("/api/health").permitAll()
                        .requestMatchers("/actuator/health").permitAll()

                        // Public GitHub analysis
                        .requestMatchers("/api/github/**").permitAll()

                        // AI endpoints are rate-limited separately
                        .requestMatchers("/api/ai/**").permitAll()

                        // Developer reports require authentication
                        .requestMatchers("/api/reports/**").authenticated()

                        // Preserve existing public behavior for other endpoints
                        .anyRequest().permitAll()
                )

                /*
                 * Exception handling
                 */
                .exceptionHandling(ex -> ex

                        .authenticationEntryPoint(
                                (request, response, authException) ->
                                        writeJson(
                                                response,
                                                HttpServletResponse.SC_UNAUTHORIZED,
                                                "Not authenticated"
                                        )
                        )

                        .accessDeniedHandler(
                                (request, response, accessDeniedException) ->
                                        writeJson(
                                                response,
                                                HttpServletResponse.SC_FORBIDDEN,
                                                "Access denied"
                                        )
                        )
                )

                /*
                 * Custom filters
                 */
                .addFilterBefore(
                        gitHubRateLimitFilter,
                        UsernamePasswordAuthenticationFilter.class
                )

                .addFilterBefore(
                        aiRateLimitFilter,
                        UsernamePasswordAuthenticationFilter.class
                )

                .addFilterBefore(
                        internalApiKeyFilter,
                        UsernamePasswordAuthenticationFilter.class
                )

                .addFilterBefore(
                        jwtAuthFilter,
                        UsernamePasswordAuthenticationFilter.class
                )

                /*
                 * CSRF cookie generation
                 *
                 * CsrfFilter creates/loads the deferred CSRF token.
                 * This filter accesses it and therefore causes the
                 * CookieCsrfTokenRepository to send the XSRF-TOKEN cookie.
                 */
                .addFilterAfter(
                        new CsrfCookieFilter(),
                        org.springframework.security.web.csrf.CsrfFilter.class
                );

        return http.build();
    }

    /**
     * Writes the standard API error response.
     */
    private void writeJson(
            HttpServletResponse response,
            int status,
            String message
    ) throws java.io.IOException {

        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        response.getWriter().write(
                objectMapper.writeValueAsString(
                        new ApiResponse<>(false, message, null)
                )
        );
    }
}
