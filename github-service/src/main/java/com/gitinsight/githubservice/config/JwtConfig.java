package com.gitinsight.githubservice.config;

import com.gitinsight.common.security.JwtAuthFilter;
import com.gitinsight.common.security.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the shared {@link JwtUtil}/{@link JwtAuthFilter} from the common
 * module for github-service, which only validates tokens minted by
 * auth-service (validation-only constructor). Kept separate from
 * {@link SecurityConfig} so the filter bean can be injected there without a
 * circular reference.
 *
 * <p>The minimum secret length is configurable per deployment:
 * {@code JWT_MIN_SECRET_BYTES=64} in production, 32 in local dev.
 */
@Configuration
public class JwtConfig {

    @Bean
    public JwtUtil jwtUtil(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.min-secret-bytes:32}") int minSecretBytes) {
        return new JwtUtil(secret, minSecretBytes);
    }

    @Bean
    public JwtAuthFilter jwtAuthFilter(JwtUtil jwtUtil) {
        return new JwtAuthFilter(jwtUtil);
    }
}
