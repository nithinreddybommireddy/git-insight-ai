package com.gitinsight.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Locale;
import java.util.Set;

/**
 * Shared JWT signing/validation for the GitInsight services.
 *
 * <p>Centralized here so auth-service (which mints tokens at login/refresh)
 * and github-service (which validates them for {@code /api/reports/**}) can
 * never drift apart in claim names, algorithm, or secret derivation. The
 * class is intentionally <em>not</em> a Spring {@code @Component}: each
 * service registers the instance it needs as a {@code @Bean} (analytics and
 * other services scan {@code com.gitinsight} and must not pick this up).
 *
 * <p>Use the full constructor where token issuance is needed (auth-service);
 * use the validation-only constructor where tokens are only checked
 * (github-service). Issuance methods on a validation-only instance throw.
 *
 * <p>Secret policy: always reject secrets shorter than {@code minSecretBytes}
 * and known weak/default values — the app must never boot with a guessable
 * signing key. Production deployments should raise {@code minSecretBytes}
 * (e.g. 64) via {@code JWT_MIN_SECRET_BYTES}.
 */
public class JwtUtil {

    private static final int DEFAULT_MIN_SECRET_BYTES = 32;

    /** Known weak/default secrets that must never be accepted, whatever their length. */
    private static final Set<String> WEAK_SECRETS = Set.of(
            "secret", "changeme", "change-me", "change_me",
            "password", "passw0rd", "your-256-bit-secret", "your-256-bit-secret-key",
            "jwt-secret", "jwt_secret", "jwtsecret", "default", "test",
            "12345678", "12345678901234567890123456789012"
    );

    private final SecretKey secretKey;
    private final long expirationMs;
    private final long refreshExpirationMs;
    private final boolean canIssueTokens;

    /** Full constructor: mint and validate tokens (auth-service). */
    public JwtUtil(String secret, long expirationMs, long refreshExpirationMs, int minSecretBytes) {
        requireStrongSecret(secret, minSecretBytes);
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
        this.refreshExpirationMs = refreshExpirationMs;
        this.canIssueTokens = true;
    }

    /** Full constructor with the default minimum secret length (32 bytes). */
    public JwtUtil(String secret, long expirationMs, long refreshExpirationMs) {
        this(secret, expirationMs, refreshExpirationMs, DEFAULT_MIN_SECRET_BYTES);
    }

    /** Validation-only constructor: validate tokens with a raised minimum (github-service). */
    public JwtUtil(String secret, int minSecretBytes) {
        requireStrongSecret(secret, minSecretBytes);
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = 0;
        this.refreshExpirationMs = 0;
        this.canIssueTokens = false;
    }

    /** Validation-only constructor with the default minimum secret length (32 bytes). */
    public JwtUtil(String secret) {
        this(secret, DEFAULT_MIN_SECRET_BYTES);
    }

    public String generateToken(Long userId, String email, String role) {
        requireIssuer();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(secretKey)
                .compact();
    }

    public String generateRefreshToken(Long userId) {
        requireIssuer();
        return Jwts.builder()
                .subject(userId.toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshExpirationMs))
                .signWith(secretKey)
                .compact();
    }

    public Long getUserIdFromToken(String token) {
        return Long.parseLong(parseToken(token).getSubject());
    }

    public String getEmailFromToken(String token) {
        return parseToken(token).get("email", String.class);
    }

    public String getRoleFromToken(String token) {
        return parseToken(token).get("role", String.class);
    }

    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private void requireIssuer() {
        if (!canIssueTokens) {
            throw new IllegalStateException(
                    "This JwtUtil instance is validation-only (no signing expiration configured). "
                    + "Use the full constructor to issue tokens.");
        }
    }

    private static void requireStrongSecret(String secret, int minSecretBytes) {
        if (secret == null) {
            throw new IllegalArgumentException(
                    "JWT secret is missing — set JWT_SECRET (at least " + minSecretBytes + " bytes) before starting");
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < minSecretBytes) {
            throw new IllegalArgumentException(
                    "JWT secret must be at least " + minSecretBytes + " bytes — refusing to boot with a weak signing key");
        }
        String normalized = secret.trim().toLowerCase(Locale.ROOT);
        if (WEAK_SECRETS.contains(normalized)) {
            throw new IllegalArgumentException(
                    "JWT secret is a known weak/default value — refusing to boot with a guessable signing key");
        }
    }

    private Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
