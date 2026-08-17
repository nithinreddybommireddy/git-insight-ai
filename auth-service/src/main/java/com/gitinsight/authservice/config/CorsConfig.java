package com.gitinsight.authservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Env-driven CORS for auth-service. Only needed when the frontend is hosted on
 * a different origin than this service (e.g. static frontend + containerized
 * backend). Same-origin deployments (dev proxy, the Docker nginx gateway) never
 * send cross-origin requests, so no CORS header is produced.
 *
 * <p>{@code app.cors.allowed-origins} is a comma-separated allowlist
 * (default {@code http://localhost:5173}). Credentials are enabled so the
 * HttpOnly session cookies (set by login/register/OAuth) are sent on
 * cross-origin XHR; the Authorization header remains supported for API
 * clients.
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins:http://localhost:5173}") String allowedOrigins) {
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Cookie"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
