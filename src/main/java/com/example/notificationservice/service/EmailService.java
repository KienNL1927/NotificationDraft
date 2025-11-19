package com.example.notificationservice.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.notification.email.from}")
    private String fromEmail;

    @Value("${app.notification.email.from-name}")
    private String fromName;

    /**
     * Send HTML email asynchronously
     * @param to Recipient email address
     * @param subject Email subject
     * @param htmlContent HTML content
     * @return CompletableFuture with success status
     */
    @Async("emailExecutor")
    public CompletableFuture<Boolean> sendEmail(String to, String subject, String htmlContent) {
        try {
            log.info("Attempting to send email to: {}", to);
            log.debug("Email config - From: {}, SMTP Host: {}", fromEmail,
                    mailSender.toString());

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail, fromName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("✅ Email sent successfully to: {}", to);
            return CompletableFuture.completedFuture(true);

        } catch (MessagingException e) {
            log.error("❌ MessagingException sending email to {}: {}", to, e.getMessage(), e);
            return CompletableFuture.completedFuture(false);
        } catch (Exception e) {
            log.error("❌ Failed to send email to {}: {}", to, e.getMessage(), e);
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * Send plain text email asynchronously
     * @param to Recipient email address
     * @param subject Email subject
     * @param content Plain text content
     * @return CompletableFuture with success status
     */
    @Async("emailExecutor")
    public CompletableFuture<Boolean> sendPlainTextEmail(String to, String subject, String content) {
        try {
            log.info("Attempting to send plain text email to: {}", to);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");

            helper.setFrom(fromEmail, fromName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(content, false);

            mailSender.send(message);
            log.info("✅ Plain text email sent successfully to: {}", to);
            return CompletableFuture.completedFuture(true);

        } catch (MessagingException e) {
            log.error("❌ MessagingException sending plain text email to {}: {}", to, e.getMessage(), e);
            return CompletableFuture.completedFuture(false);
        } catch (Exception e) {
            log.error("❌ Failed to send plain text email to {}: {}", to, e.getMessage(), e);
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * Synchronous version for testing/debugging
     */
    public boolean sendEmailSync(String to, String subject, String htmlContent) {
        try {
            log.info("Sending email synchronously to: {}", to);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail, fromName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("✅ Email sent synchronously to: {}", to);
            return true;

        } catch (Exception e) {
            log.error("❌ Failed to send email synchronously to {}: {}", to, e.getMessage(), e);
            return false;
        }
    }
}