package com.gitinsight.githubservice.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gitinsight.common.dto.response.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.MessageDigest;

/**
 * Server-to-server authentication for internal endpoints (currently
 * {@code /api/ai/job-match}).
 *
 * <p>The recruiter AI flow runs inside auth-service, which has already enforced
 * {@code RECRUITER/ADMIN} authorization before calling this endpoint. The
 * public internet must not be able to reach it directly (it invokes Gemini and
 * would bypass the recruiter role check), so the call must present the shared
 * {@code INTERNAL_API_KEY} in {@code X-Internal-Api-Key}.
 *
 * <p>Fail-closed policy: when {@code INTERNAL_API_KEY} is not configured the
 * endpoint returns 503 — a recruiter AI explanation request degrades gracefully
 * to the deterministic ranking on the auth-service side. The header is compared
 * via a SHA-256 digest to avoid timing side channels on the plaintext secret.
 */
@Component
public class InternalApiKeyFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Internal-Api-Key";
    private static final String PROTECTED_PATH = "/api/ai/job-match";

    private final String configuredDigest;
    private final ObjectMapper objectMapper;

    public InternalApiKeyFilter(@Value("${app.security.internal-api-key:}") String internalApiKey,
                                ObjectMapper objectMapper) {
        this.configuredDigest = (internalApiKey == null || internalApiKey.isBlank())
                ? null
                : sha256(internalApiKey);
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !PROTECTED_PATH.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (configuredDigest == null) {
            write(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "Internal AI endpoints are not configured. Set INTERNAL_API_KEY.");
            return;
        }

        String provided = request.getHeader(HEADER);
        boolean ok = provided != null && !provided.isBlank()
                && MessageDigest.isEqual(
                        sha256(provided).getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        configuredDigest.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if (!ok) {
            write(response, HttpServletResponse.SC_FORBIDDEN, "Forbidden");
            return;
        }

        chain.doFilter(request, response);
    }

    private void write(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(new ApiResponse<>(false, message, null)));
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
