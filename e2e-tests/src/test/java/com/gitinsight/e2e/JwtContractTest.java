package com.gitinsight.e2e;

import com.gitinsight.common.security.JwtUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cross-service JWT contract test (plain unit test — no Spring context).
 *
 * <p>auth-service mints JWTs at login; github-service validates them for
 * {@code /api/reports/**}. Both sides now share ONE {@code JwtUtil} from the
 * common module (same secret policy, claim contract — {@code sub} = userId,
 * {@code email}, {@code role}; HS256; the same byte-secret derivation), so the
 * historical drift seam is closed at compile time. This test drives the shared
 * class through both the issuing and validation-only constructors to prove the
 * runtime contract the services depend on.
 *
 * <p>Endpoint-level enforcement (Bearer token → 200, no/garbage token → 401)
 * is covered by {@code GitHubFlowIntegrationTest} in github-service.
 */
class JwtContractTest {

    /** Mirrors github-service's test profile secret (application-test.yml). */
    private static final String SHARED_SECRET = "integration-test-secret-key-at-least-32-bytes-long";

    private static final JwtUtil AUTH = new JwtUtil(SHARED_SECRET, 3_600_000L, 604_800_000L);

    private static final JwtUtil GITHUB = new JwtUtil(SHARED_SECRET);

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
        JwtUtil other = new JwtUtil("a-completely-different-secret-key-at-least-32-bytes-long");

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
    void validationOnlyInstanceCannotIssueTokens() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> GITHUB.generateToken(1L, "user@example.com", "USER"));
    }

    @Test
    void weakSecretsAreRejectedAtConstruction() {
        // Both services share the same strength policy — short secrets and
        // known weak defaults must refuse to boot.
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new JwtUtil("too-short", 3_600_000L, 604_800_000L));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new JwtUtil("too-short"));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new JwtUtil("changeme", 3_600_000L, 604_800_000L));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new JwtUtil("your-256-bit-secret"));
    }

    @Test
    void raisedMinimumLengthIsEnforced() {
        // 32-byte secret passes the default policy but fails the production
        // (64-byte) policy — the strictness knob deployments rely on.
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> new JwtUtil(SHARED_SECRET));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new JwtUtil(SHARED_SECRET, 64));
    }
}
