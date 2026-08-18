package com.gitinsight.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Ensures the CSRF token cookie is rendered on every response.
 *
 * <p>Spring Security 6 uses deferred/lazy CSRF token loading via
 * {@code CsrfTokenRequestAttributeHandler}. Without this filter the
 * {@code XSRF-TOKEN} cookie is never written to responses, so a
 * single-page application cannot read the cookie and send the
 * {@code X-XSRF-TOKEN} header on mutating requests — resulting in
 * a {@code 403 Forbidden} on every POST/PUT/DELETE.
 *
 * <p>Placed <em>after</em> the {@code CsrfFilter} in the chain so the
 * deferred token is already resolved in the request attributes. Calling
 * {@link CsrfToken#getTokenValue()} forces the deferred token to load,
 * which triggers {@code CookieCsrfTokenRepository.saveToken()} and writes
 * the {@code XSRF-TOKEN} cookie on the response.
 *
 * <p>Shared by auth-service and github-service — each service registers
 * this filter in its own {@code SecurityFilterChain}.
 */
public final class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken != null) {
            // Force-load the deferred token so CookieCsrfTokenRepository
            // writes the XSRF-TOKEN cookie on this response.
            csrfToken.getToken();
        }
        filterChain.doFilter(request, response);
    }
}
