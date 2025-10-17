package com.example.notificationservice.listener;


import com.example.notificationservice.enums.NotificationChannel;
import com.example.notificationservice.event.inbound.AssessmentPublishedEvent;
import com.example.notificationservice.event.inbound.ProctoringViolationEvent;
import com.example.notificationservice.event.inbound.SessionCompletedEvent;
import com.example.notificationservice.event.inbound.UserRegisteredEvent;
import com.example.notificationservice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class EventListener {

    private final NotificationService notificationService;

    @KafkaListener(topics = "${app.kafka.topics.inbound.user-events}",
            groupId = "${spring.kafka.consumer.group-id}")
    public void handleUserRegistered(UserRegisteredEvent event) {
        log.info("Received user.registered event for user: {}", event.getUserId());

        Map<String, Object> data = new HashMap<>();
        data.put("username", event.getUsername());
        data.put("email", event.getEmail());
        data.put("firstName", event.getFirstName());
        data.put("lastName", event.getLastName());

        notificationService.processNotification(
                "user.registered",
                event.getUserId(),
                event.getEmail(),
                data,
                List.of(NotificationChannel.EMAIL)
        );
    }

    @KafkaListener(topics = "${app.kafka.topics.inbound.assessment-events}",
            groupId = "${spring.kafka.consumer.group-id}")
    public void handleSessionCompleted(SessionCompletedEvent event) {
        log.info("Received session.completed event for user: {}", event.getUserId());

        Map<String, Object> data = new HashMap<>();
        data.put("username", event.getUsername());
        data.put("assessmentName", event.getAssessmentName());
        data.put("completionTime", event.getCompletionTime());
        data.put("score", event.getScore());
        data.put("status", event.getStatus());

        notificationService.processNotification(
                "session.completed",
                event.getUserId(),
                event.getEmail(),
                data,
                List.of(NotificationChannel.EMAIL)
        );
    }

    @KafkaListener(topics = "${app.kafka.topics.inbound.proctoring-events}",
            groupId = "${spring.kafka.consumer.group-id}")
    public void handleProctoringViolation(ProctoringViolationEvent event) {
        log.info("Received proctoring.violation event for session: {}", event.getSessionId());

        Map<String, Object> data = new HashMap<>();
        data.put("username", event.getUsername());
        data.put("sessionId", event.getSessionId());
        data.put("violationType", event.getViolationType());
        data.put("timestamp", event.getTimestamp().toString());
        data.put("severity", event.getSeverity());

        // Notify proctors via WebSocket
        for (Integer proctorId : event.getProctorIds()) {
            notificationService.processNotification(
                    "proctoring.violation",
                    proctorId,
                    null, // Proctors notified via WebSocket only
                    data,
                    List.of(NotificationChannel.SSE, NotificationChannel.EMAIL)
            );
        }
    }

    @KafkaListener(topics = "${app.kafka.topics.inbound.assessment-events}",
            groupId = "${spring.kafka.consumer.group-id}")
    public void handleAssessmentPublished(AssessmentPublishedEvent event) {
        log.info("Received assessment.published event for assessment: {}", event.getAssessmentId());

        Map<String, Object> data = new HashMap<>();
        data.put("assessmentName", event.getAssessmentName());
        data.put("duration", event.getDuration());
        data.put("dueDate", event.getDueDate());

        // Send notification to all assigned users
        for (AssessmentPublishedEvent.UserInfo user : event.getAssignedUsers()) {
            data.put("username", user.getUsername());

            notificationService.processNotification(
                    "assessment.published",
                    user.getUserId(),
                    user.getEmail(),
                    data,
                    List.of(NotificationChannel.SSE, NotificationChannel.EMAIL)
            );
        }
    }
}