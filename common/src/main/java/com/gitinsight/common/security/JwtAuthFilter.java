package com.gitinsight.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Shared Bearer-JWT extraction filter used by auth-service and github-service.
 *
 * <p>Extracts a {@code Authorization: Bearer <token>} header (tokens minted by
 * auth-service), validates it against the shared {@link JwtUtil}, and populates
 * the Spring Security context so authenticated-only endpoints can authorize the
 * request. Requests without a valid token simply continue unauthenticated —
 * each service's {@code SecurityFilterChain} decides which paths require auth.
 *
 * <p>Intentionally <em>not</em> a Spring {@code @Component}: services register
 * it as a {@code @Bean} so services that do not need JWT (e.g. analytics) are
 * unaffected by the shared security code.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    public JwtAuthFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String token = extractToken(request);

        // Only access tokens authorize API requests. A stolen refresh token must
        // never double as a bearer token (refresh tokens carry no role claim).
        if (token != null
                && jwtUtil.validateToken(token)
                && JwtUtil.TOKEN_TYPE_ACCESS.equals(jwtUtil.getTokenType(token))) {
            Long userId = jwtUtil.getUserIdFromToken(token);
            String role = jwtUtil.getRoleFromToken(token);

            var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
            var authentication = new UsernamePasswordAuthenticationToken(userId, null, authorities);

            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        // HttpOnly cookie transport (browser sessions set by auth-service at
        // login/register/OAuth). The Authorization header remains the primary
        // channel for API clients and server-to-server calls; the cookie is the
        // fallback so the browser can authenticate without touching tokens.
        jakarta.servlet.http.Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (jakarta.servlet.http.Cookie cookie : cookies) {
                if (AuthCookieNames.ACCESS.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
