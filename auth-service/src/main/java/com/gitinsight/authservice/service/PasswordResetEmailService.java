package com.gitinsight.authservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

/**
 * Sends password reset emails when {@code MAIL_ENABLED=true} and a
 * {@link JavaMailSender} is available. When {@code MAIL_ENABLED=false}
 * (local development), the email is simply not sent — the token is
 * never logged because a reset token is a temporary credential.
 *
 * <p>For local testing without SMTP, use the {@code /api/auth/dev/reset-token}
 * endpoint (only available in dev/test profiles) to retrieve the token directly.
 */
@Service
public class PasswordResetEmailService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetEmailService.class);

    private final JavaMailSender mailSender;
    private final String mailFrom;
    private final String frontendUrl;
    private final boolean mailEnabled;

    public PasswordResetEmailService(
            @Value("${app.mail.enabled:false}") boolean mailEnabled,
            @Value("${app.mail.from:noreply@gitinsightai.com}") String mailFrom,
            @Value("${app.frontend-url:https://git-insight-ai-one.vercel.app}") String frontendUrl,
            ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.mailEnabled = mailEnabled;
        this.mailFrom = mailFrom;
        this.frontendUrl = frontendUrl;
        this.mailSender = mailSenderProvider.getIfAvailable();
    }

    @Async
    public void sendResetEmail(String toEmail, String userName, String rawToken) {
        String resetUrl = frontendUrl + "/auth/reset-password?token=" + rawToken;

        if (!mailEnabled || mailSender == null) {
            // SECURITY: Never log the raw token or reset URL. A password-reset
            // token is a temporary credential — logging it exposes the account
            // to anyone with log access (operators, log aggregators, CI output).
            // For local dev testing, use the dev-only test endpoint instead.
            log.warn("MAIL_ENABLED=false — password reset email not sent to: {} (use dev profile to test)", toEmail);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(mailFrom);
            helper.setTo(toEmail);
            helper.setSubject("Reset your GitInsight AI password");
            helper.setText(buildEmailBody(userName, resetUrl), true);
            mailSender.send(message);
            log.info("Password reset email sent to: {}", toEmail);
        } catch (MessagingException e) {
            log.error("Failed to send password reset email to: {}", toEmail, e);
            // Do not throw — the token is already generated and valid.
            // The user can request another reset link.
        }
    }

    private static String buildEmailBody(String userName, String resetUrl) {
        String displayName = (userName != null && !userName.isBlank()) ? userName : "there";
        return """
                <!DOCTYPE html>
                <html>
                <head><meta charset="UTF-8"></head>
                <body style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; line-height: 1.6; color: #333; max-width: 600px; margin: 0 auto; padding: 20px;">
                    <div style="background: #f8f9fa; border-radius: 12px; padding: 32px; margin-top: 20px;">
                        <h2 style="color: #1a1a2e; margin-top: 0;">Reset your password</h2>
                        <p>Hi %s,</p>
                        <p>We received a request to reset your GitInsight AI password. Click the button below to set a new password:</p>
                        <div style="text-align: center; margin: 32px 0;">
                            <a href="%s"
                               style="display: inline-block; background: #6366f1; color: white; padding: 12px 32px; border-radius: 8px; text-decoration: none; font-weight: 600;">
                                Reset Password
                            </a>
                        </div>
                        <p style="color: #666; font-size: 14px;">This link expires in 30 minutes. If you didn't request a password reset, you can safely ignore this email.</p>
                        <hr style="border: none; border-top: 1px solid #e5e7eb; margin: 24px 0;">
                        <p style="color: #999; font-size: 12px;">GitInsight AI &mdash; Developer Analytics Platform</p>
                    </div>
                </body>
                </html>
                """.formatted(displayName, resetUrl);
    }
}
