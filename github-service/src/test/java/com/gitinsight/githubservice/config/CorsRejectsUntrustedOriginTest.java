package com.gitinsight.githubservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CORS behavior of github-service — documents the exact production failure
 * mode behind the "Generate Report → 403" bug and the contract the gateway
 * edge now upholds.
 *
 * <p>Production failure: the gateway forwarded the browser's {@code Origin}
 * header on the {@code POST /api/reports/generate/{username}} call. This
 * service's Spring CORS filter re-evaluated that origin against
 * {@code app.cors.allowed-origins} (default {@code http://localhost:5173}) and
 * rejected it with <b>403 "Invalid CORS request"</b> — while all same-origin
 * GETs (no Origin header) kept working. The gateway's OriginStripFilter now
 * removes the header before forwarding, so this filter is inert for proxied
 * traffic.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CorsRejectsUntrustedOriginTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void postWithDisallowedOriginIsRejectedWith403() throws Exception {
        // The exact production failure: POST carrying an Origin the service
        // does not allow → Spring CORS filter rejects before the controller.
        mockMvc.perform(post("/api/github/torvalds/score")
                        .header("Origin", "https://some-untrusted-origin.example.com"))
                .andExpect(status().isForbidden());
    }

    @Test
    void preflightFromDisallowedOriginIsRejected() throws Exception {
        mockMvc.perform(options("/api/reports/generate/nithin-marla")
                        .header("Origin", "https://some-untrusted-origin.example.com")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    void preflightFromAllowedLocalOriginIsAccepted() throws Exception {
        // Default test/dev allowlist includes http://localhost:5173.
        mockMvc.perform(options("/api/reports/generate/nithin-marla")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    void postWithAllowedOriginPassesCorsAndReachesSecurity() throws Exception {
        // With an allowed origin the CORS filter lets the POST through; the
        // security layer then answers 401 (no token) — proving the endpoint
        // itself and the auth chain still work. Contrast with the 403 above:
        // that is exactly the production failure the gateway strip fixes.
        mockMvc.perform(post("/api/ai/commit-diff-review")
                        .header("Origin", "http://localhost:5173"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void requestWithoutOriginIsNotTreatedAsCors() throws Exception {
        // Server-to-server / gateway-proxied calls carry no Origin header —
        // the CORS filter must not reject them (security still applies).
        mockMvc.perform(post("/api/ai/commit-diff-review"))
                .andExpect(status().isUnauthorized());
    }
}
