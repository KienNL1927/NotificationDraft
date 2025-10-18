package com.example.notificationservice.service;

import com.example.notificationservice.entity.Notification;
import com.example.notificationservice.entity.NotificationPreference;
import com.example.notificationservice.entity.NotificationTemplate;
import com.example.notificationservice.enums.NotificationChannel;
import com.example.notificationservice.enums.NotificationStatus;
import com.example.notificationservice.event.outbound.NotificationFailedEvent;
import com.example.notificationservice.event.outbound.NotificationSentEvent;
import com.example.notificationservice.repository.NotificationPreferenceRepository;
import com.example.notificationservice.repository.NotificationRepository;
import com.example.notificationservice.repository.NotificationTemplateRepository;
import com.example.notificationservice.util.TemplateEngine;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationTemplateRepository templateRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final EmailService emailService;
    private final SseEmitterService sseEmitterService;
    private final TemplateEngine templateEngine;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.notification.retry.max-attempts}")
    private int maxRetryAttempts;

    @Value("${app.kafka.topics.outbound.notification-events}")
    private String notificationEventsTopic;

    @Transactional
    public void processNotification(String eventType, Integer userId, String email,
                                    Map<String, Object> data, List<NotificationChannel> channels) {

        // Check user preferences
        Optional<NotificationPreference> preference = preferenceRepository.findByUserId(userId);

        // Get template based on event type
        String templateName = mapEventToTemplate(eventType);
        Optional<NotificationTemplate> templateOpt = templateRepository.findByName(templateName);

        if (templateOpt.isEmpty()) {
            log.error("Template not found for event type: {}", eventType);
            return;
        }

        NotificationTemplate template = templateOpt.get();

        // Process content with template engine
        String processedContent = templateEngine.processTemplate(template.getBody(), data);
        String processedSubject = template.getSubject() != null ?
                templateEngine.processTemplate(template.getSubject(), data) : "";

        // Create notification record
        Notification notification = Notification.builder()
                .recipientId(userId)
                .recipientEmail(email)
                .type(eventType)
                .subject(processedSubject)
                .content(processedContent)
                .template(template)
                .status(NotificationStatus.PENDING)
                .build();

        // Send through appropriate channels based on preferences
        for (NotificationChannel channel : channels) {
            if (shouldSendToChannel(preference, channel)) {
                notification.setChannel(channel);
                Notification saved = notificationRepository.save(notification);
                sendNotification(saved);
            }
        }
    }

    @Async
    public void sendNotification(Notification notification) {
        try {
            boolean sent = false;

            switch (notification.getChannel()) {
                case EMAIL:
                    sent = emailService.sendEmail(
                            notification.getRecipientEmail(),
                            notification.getSubject(),
                            notification.getContent()
                    );
                    break;

                case SSE:
                    sent = sseEmitterService.sendToUser(
                            notification.getRecipientId(),
                            notification.getType(),
                            notification.getContent()
                    );
                    break;

                case PUSH:
                    // Push notification implementation would go here
                    log.warn("Push channel not implemented yet");
                    break;
            }

            if (sent) {
                updateNotificationStatus(notification, NotificationStatus.SENT);
                publishNotificationSentEvent(notification);
            } else {
                handleFailedNotification(notification, "Failed to send notification");
            }

        } catch (Exception e) {
            log.error("Error sending notification: {}", e.getMessage(), e);
            handleFailedNotification(notification, e.getMessage());
        }
    }

    private void handleFailedNotification(Notification notification, String errorMessage) {
        notification.setErrorMessage(errorMessage);
        notification.setRetryCount(notification.getRetryCount() + 1);

        boolean willRetry = notification.getRetryCount() < maxRetryAttempts;

        if (!willRetry) {
            notification.setStatus(NotificationStatus.FAILED);
        }

        notificationRepository.save(notification);
        publishNotificationFailedEvent(notification, willRetry);

        if (willRetry) {
            // Retry will be handled by scheduled method
            log.info("Notification {} scheduled for retry (attempt {})",
                    notification.getId(), notification.getRetryCount());
        }
    }

    @Scheduled(fixedDelayString = "${app.notification.retry.delay-ms}")
    public void retryFailedNotifications() {
        List<Notification> failedNotifications = notificationRepository
                .findByStatusAndRetryCountLessThan(NotificationStatus.PENDING, maxRetryAttempts);

        for (Notification notification : failedNotifications) {
            log.info("Retrying notification {}", notification.getId());
            sendNotification(notification);
        }
    }

    private void updateNotificationStatus(Notification notification, NotificationStatus status) {
        notification.setStatus(status);
        notification.setSentAt(LocalDateTime.now());
        if (status == NotificationStatus.DELIVERED) {
            notification.setDeliveredAt(LocalDateTime.now());
        }
        notificationRepository.save(notification);
    }

    private boolean shouldSendToChannel(Optional<NotificationPreference> preference,
                                        NotificationChannel channel) {
        if (preference.isEmpty()) {
            return true; // Default to sending if no preference
        }

        NotificationPreference pref = preference.get();
        return switch (channel) {
            case EMAIL -> pref.getEmailEnabled();
            case SSE -> pref.getSseEnabled();
            case PUSH -> pref.getPushEnabled();
        };
    }

    private String mapEventToTemplate(String eventType) {
        return switch (eventType) {
            case "user.registered" -> "welcome_user";
            case "session.completed" -> "session_completion";
            case "proctoring.violation" -> "proctoring_alert";
            case "assessment.published" -> "new_assessment_assigned";
            default -> eventType.replace(".", "_");
        };
    }

    private void publishNotificationSentEvent(Notification notification) {
        NotificationSentEvent event = NotificationSentEvent.builder()
                .notificationId(notification.getId())
                .recipientId(notification.getRecipientId())
                .channel(notification.getChannel().toString())
                .type(notification.getType())
                .status("sent")
                .deliveryTime(LocalDateTime.now().toString())
                .build();
        event.init();

        kafkaTemplate.send(notificationEventsTopic, event);
    }

    private void publishNotificationFailedEvent(Notification notification, boolean willRetry) {
        NotificationFailedEvent event = NotificationFailedEvent.builder()
                .notificationId(notification.getId())
                .recipientId(notification.getRecipientId())
                .channel(notification.getChannel().toString())
                .errorMessage(notification.getErrorMessage())
                .retryCount(notification.getRetryCount())
                .willRetry(willRetry)
                .build();
        event.init();

        kafkaTemplate.send(notificationEventsTopic, event);
    }
}