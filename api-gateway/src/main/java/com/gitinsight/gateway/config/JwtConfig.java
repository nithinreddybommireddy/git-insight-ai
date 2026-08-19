package com.gitinsight.gateway.config;

import com.gitinsight.common.security.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the shared {@link JwtUtil} for the API gateway.
 *
 * <p>The gateway uses a <em>validation-only</em> instance: it never mints
 * tokens. This lets the gateway reject invalid/expired tokens before
 * forwarding requests to downstream services, providing a first line of
 * defense without duplicating auth-service's JWT logic.
 */
@Configuration
public class JwtConfig {

    @Bean
    public JwtUtil jwtUtil(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.min-secret-bytes:32}") int minSecretBytes) {
        return new JwtUtil(secret, minSecretBytes);
    }
}
