package com.gitinsight.authservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gitinsight.authservice.security.RateLimitFilter;
import com.gitinsight.common.dto.response.ApiResponse;
import com.gitinsight.common.security.CsrfCookieFilter;
import com.gitinsight.common.security.JwtAuthFilter;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

import org.springframework.security.config.http.SessionCreationPolicy;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final RateLimitFilter rateLimitFilter;
    private final ObjectMapper objectMapper;

    public SecurityConfig(
            JwtAuthFilter jwtAuthFilter,
            RateLimitFilter rateLimitFilter,
            ObjectMapper objectMapper
    ) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.rateLimitFilter = rateLimitFilter;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http
    ) throws Exception {

        http

                // =========================================================
                // CSRF — consistent XSRF-TOKEN / X-XSRF-TOKEN names shared
                //        with github-service so a single axios instance can
                //        read the cookie and send the header for ALL requests.
                // =========================================================
                .csrf(csrf -> csrf

                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(
                                new CsrfTokenRequestAttributeHandler()
                        )

                        /*
                         * Authentication endpoints are intentionally
                         * excluded because login/register/refresh/logout
                         * establish or change the authentication cookie.
                         */
                        .ignoringRequestMatchers(
                                "/api/auth/**",
                                "/api/health",
                                "/actuator/health"
                        )
                )

                // =========================================================
                // CORS
                // =========================================================
                .cors(Customizer.withDefaults())

                // =========================================================
                // Stateless authentication
                // =========================================================
                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                // =========================================================
                // Authorization
                // =========================================================
                .authorizeHttpRequests(auth -> auth

                        // Authentication + Password Reset
                        .requestMatchers(
                                "/api/auth/register",
                                "/api/auth/login",
                                "/api/auth/refresh",
                                "/api/auth/logout",
                                "/api/auth/forgot-password",
                                "/api/auth/reset-password",
                                "/api/auth/oauth/**"
                        ).permitAll()

                        // Health
                        .requestMatchers(
                                "/api/health",
                                "/actuator/health"
                        ).permitAll()

                        // Everything else requires authentication
                        .anyRequest().authenticated()
                )

                // =========================================================
                // Exception handling
                // =========================================================
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

                // =========================================================
                // Custom filters
                // =========================================================

                /*
                 * Rate limiter
                 */
                .addFilterBefore(
                        rateLimitFilter,
                        UsernamePasswordAuthenticationFilter.class
                )

                /*
                 * JWT authentication
                 */
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

    // =========================================================
    // PASSWORD ENCODER
    // =========================================================

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // =========================================================
    // JSON ERROR RESPONSE
    // =========================================================

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
                        new ApiResponse<>(
                                false,
                                message,
                                null
                        )
                )
        );
    }
}
