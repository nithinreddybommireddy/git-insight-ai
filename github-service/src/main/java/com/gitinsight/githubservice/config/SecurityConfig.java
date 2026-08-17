package com.gitinsight.githubservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gitinsight.common.dto.response.ApiResponse;
import com.gitinsight.common.security.JwtAuthFilter;
import com.gitinsight.githubservice.security.AiRateLimitFilter;
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

/**
 * github-service security.
 *
 * <p>Public analysis is intentional (anyone can look up a GitHub developer),
 * so the GitHub + AI surfaces stay open. The AI endpoints are additionally
 * protected by the per-client Redis rate limiter ({@link AiRateLimitFilter}) —
 * every call can consume Gemini quota, so public access is fine but unbounded
 * access is not. The report endpoints persist score history and expose
 * aggregates, so they require a valid auth-service JWT and are scoped per
 * role (own reports for USER, all for RECRUITER/ADMIN).
 *
 * <p>{@code /api/ai/job-match} stays public by design: it is an internal
 * server-to-server endpoint called by auth-service's recruiter flow, which has
 * already enforced RECRUITER/ADMIN authorization before reaching it (it is
 * still covered by the AI rate limiter).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final AiRateLimitFilter aiRateLimitFilter;
    private final ObjectMapper objectMapper;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter,
                          AiRateLimitFilter aiRateLimitFilter,
                          ObjectMapper objectMapper) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.aiRateLimitFilter = aiRateLimitFilter;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/health").permitAll()
                        .requestMatchers("/api/github/**").permitAll()
                        .requestMatchers("/api/ai/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/api/reports/**").authenticated()
                        .anyRequest().permitAll()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) ->
                                writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, "Not authenticated"))
                )
                .addFilterBefore(aiRateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private void writeJson(HttpServletResponse response, int status, String message) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(new ApiResponse<>(false, message, null)));
    }
}
