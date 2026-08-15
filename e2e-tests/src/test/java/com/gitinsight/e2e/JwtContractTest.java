package com.gitinsight.e2e;

import com.gitinsight.authservice.security.JwtUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cross-service JWT contract test (plain unit test — no Spring context).
 *
 * <p>auth-service mints JWTs at login; github-service validates them for
 * {@code /api/reports/**}. The two sides share a secret ({@code JWT_SECRET})
 * and a claim contract ({@code sub} = userId, {@code email}, {@code role};
 * HS256; the same byte-secret derivation). This test drives the REAL
 * {@code JwtUtil} from both services against each other so any drift in claim
 * names, algorithm, or secret derivation fails the build — the exact seam that
 * per-service tests (which self-mint tokens) cannot see.
 *
 * <p>Endpoint-level enforcement (Bearer token → 200, no/garbage token → 401)
 * is covered by {@code GitHubFlowIntegrationTest} in github-service.
 */
class JwtContractTest {

    /** Mirrors github-service's test profile secret (application-test.yml). */
    private static final String SHARED_SECRET = "integration-test-secret-key-at-least-32-bytes-long";

    private static final JwtUtil AUTH = new JwtUtil(SHARED_SECRET, 3_600_000L, 604_800_000L);

    private static final com.gitinsight.githubservice.security.JwtUtil GITHUB =
            new com.gitinsight.githubservice.security.JwtUtil(SHARED_SECRET);

    @Test
    void tokenMintedByAuthServiceIsAcceptedByGithubService() {
        String token = AUTH.generateToken(42L, "recruiter@example.com", "RECRUITER");

        assertThat(GITHUB.validateToken(token)).isTrue();
        // The exact claims github-service's JwtAuthFilter depends on.
        assertThat(GITHUB.getUserIdFromToken(token)).isEqualTo(42L);
        assertThat(GITHUB.getEmailFromToken(token)).isEqualTo("recruiter@example.com");
        assertThat(GITHUB.getRoleFromToken(token)).isEqualTo("RECRUITER");
    }

    @Test
    void tokenSignedWithDifferentSecretIsRejected() {
        com.gitinsight.githubservice.security.JwtUtil other =
                new com.gitinsight.githubservice.security.JwtUtil(
                        "a-completely-different-secret-key-at-least-32-bytes-long");

        String token = AUTH.generateToken(1L, "user@example.com", "USER");

        assertThat(other.validateToken(token)).isFalse();
    }

    @Test
    void expiredTokenIsRejected() {
        JwtUtil shortLivedAuth = new JwtUtil(SHARED_SECRET, -1_000L, 604_800_000L);
        String token = shortLivedAuth.generateToken(1L, "user@example.com", "USER");

        assertThat(GITHUB.validateToken(token)).isFalse();
    }

    @Test
    void weakSecretsAreRejectedAtConstruction() {
        // Both services must refuse to boot with a signing key shorter than 32 bytes.
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new JwtUtil("too-short", 3_600_000L, 604_800_000L));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new com.gitinsight.githubservice.security.JwtUtil("too-short"));
    }
}
