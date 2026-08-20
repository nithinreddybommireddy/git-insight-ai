package com.gitinsight.authservice.service;

import com.gitinsight.authservice.entity.PasswordResetToken;
import com.gitinsight.authservice.entity.User;
import com.gitinsight.authservice.repository.PasswordResetTokenRepository;
import com.gitinsight.authservice.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;

@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    private static final long TOKEN_EXPIRY_MINUTES = 30;

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetEmailService emailService;

    public PasswordResetService(UserRepository userRepository,
                                PasswordResetTokenRepository tokenRepository,
                                PasswordEncoder passwordEncoder,
                                PasswordResetEmailService emailService) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    /**
     * Request a password reset. Always returns success to prevent email enumeration.
     * If the user exists and is enabled, generates a token and sends a reset email.
     */
    @Transactional
    public void requestReset(String email) {
        String normalized = normalizeEmail(email);
        Optional<User> userOpt = userRepository.findByEmail(normalized);

        if (userOpt.isEmpty()) {
            // Log for server-side visibility but return the same response.
            log.info("Password reset requested for non-existent email (no-op): {}", normalized);
            return;
        }

        User user = userOpt.get();

        if (!user.isEnabled()) {
            log.info("Password reset requested for disabled account (no-op): {}", normalized);
            return;
        }

        // Invalidate any previous unused reset tokens for this user.
        tokenRepository.deleteByUser(user);
        tokenRepository.flush();

        // Generate a secure random token.
        String rawToken = generateSecureToken();
        String tokenHash = hashToken(rawToken);

        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setUser(user);
        resetToken.setTokenHash(tokenHash);
        resetToken.setExpiresAt(LocalDateTime.now().plusMinutes(TOKEN_EXPIRY_MINUTES));
        resetToken.setUsed(false);
        tokenRepository.save(resetToken);

        // Send the reset email (non-blocking from the user's perspective).
        emailService.sendResetEmail(user.getEmail(), user.getName(), rawToken);
    }

    /**
     * Reset the user's password using a valid, unexpired, unused token.
     *
     * @throws IllegalArgumentException if the token is invalid, expired, or already used.
     */
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("Invalid or expired reset link.");
        }

        String tokenHash = hashToken(rawToken);
        PasswordResetToken resetToken = tokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired reset link."));

        if (resetToken.isUsed()) {
            throw new IllegalArgumentException("Invalid or expired reset link.");
        }

        if (resetToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Invalid or expired reset link.");
        }

        User user = resetToken.getUser();
        if (!user.isEnabled()) {
            throw new IllegalArgumentException("Invalid or expired reset link.");
        }

        // Update password with BCrypt encoding (same encoder as registration).
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // Mark this token as used.
        resetToken.setUsed(true);
        tokenRepository.save(resetToken);

        // Invalidate any remaining tokens for this user.
        tokenRepository.deleteByUser(user);

        log.info("Password successfully reset for user: {}", user.getEmail());
    }

    /**
     * Generate a cryptographically secure random token.
     * Returns a URL-safe Base64-encoded string.
     */
    private static String generateSecureToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Hash a token using SHA-256. The raw token is never stored.
     */
    private static String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /** Emails are stored/compared lowercase so one logical address cannot create two accounts. */
    private static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
