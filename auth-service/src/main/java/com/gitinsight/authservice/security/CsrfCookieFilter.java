package com.gitinsight.authservice.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Forces Spring Security's deferred CSRF token to be generated.
 *
 * CookieCsrfTokenRepository stores the token in the
 * GITINSIGHT-XSRF-TOKEN cookie. Since Spring Security 6 uses
 * deferred CSRF tokens, explicitly accessing getToken() causes
 * the cookie to be generated and sent to the browser.
 */
public class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        CsrfToken csrfToken =
                (CsrfToken) request.getAttribute(CsrfToken.class.getName());

        if (csrfToken != null) {
            csrfToken.getToken();
        }

        filterChain.doFilter(request, response);
    }
}