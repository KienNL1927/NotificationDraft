package com.example.notificationservice.listener;

import com.example.notificationservice.enums.NotificationChannel;
import com.example.notificationservice.event.inbound.AssessmentPublishedEvent;
import com.example.notificationservice.event.inbound.ProctoringViolationEvent;
import com.example.notificationservice.event.inbound.SessionCompletedEvent;
import com.example.notificationservice.event.inbound.UserRegisteredEvent;
import com.example.notificationservice.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class RedisStreamListener {

    private final NotificationService notificationService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.redis.streams.user-events}")
    private String userEventsStream;

    @Value("${app.redis.streams.assessment-events}")
    private String assessmentEventsStream;

    @Value("${app.redis.streams.proctoring-events}")
    private String proctoringEventsStream;

    @Value("${app.redis.consumer.group-id}")
    private String consumerGroup;

    @Value("${app.redis.consumer.name}")
    private String consumerName;

    private volatile boolean running = true;

    @PostConstruct
    public void initialize() {
        // Create consumer groups for all streams
        createConsumerGroupIfNotExists(userEventsStream);
        createConsumerGroupIfNotExists(assessmentEventsStream);
        createConsumerGroupIfNotExists(proctoringEventsStream);

        log.info("Redis Stream Listener initialized for streams: {}, {}, {}",
                userEventsStream, assessmentEventsStream, proctoringEventsStream);
    }

    @PreDestroy
    public void shutdown() {
        running = false;
        log.info("Redis Stream Listener shutting down...");
    }

    /**
     * Poll messages from all streams every second
     */
    @Scheduled(fixedDelay = 1000)
    public void pollMessages() {
        if (!running) {
            return;
        }

        try {
            // Poll from each stream
            pollFromStream(userEventsStream, this::handleUserEvent);
            pollFromStream(assessmentEventsStream, this::handleAssessmentEvent);
            pollFromStream(proctoringEventsStream, this::handleProctoringEvent);
        } catch (Exception e) {
            log.error("Error polling messages from Redis streams: {}", e.getMessage(), e);
        }
    }

    private void pollFromStream(String streamKey, MessageHandler handler) {
        try {
            // Read messages from the stream for this consumer group
            List<MapRecord<String, Object, Object>> messages = redisTemplate.opsForStream()
                    .read(
                            Consumer.from(consumerGroup, consumerName),
                            StreamReadOptions.empty().count(10).block(Duration.ofSeconds(1)),
                            StreamOffset.create(streamKey, ReadOffset.lastConsumed())
                    );

            if (messages != null && !messages.isEmpty()) {
                for (MapRecord<String, Object, Object> message : messages) {
                    try {
                        log.debug("Processing message from stream '{}': {}", streamKey, message.getId());

                        // Convert map to object
                        Map<Object, Object> value = message.getValue();
                        handler.handle(value);

                        // Acknowledge the message
                        redisTemplate.opsForStream().acknowledge(consumerGroup, message);

                    } catch (Exception e) {
                        log.error("Error processing message from stream '{}': {}", streamKey, e.getMessage(), e);
                    }
                }
            }
        } catch (Exception e) {
            // This is normal when no messages are available, don't log as error
            log.trace("No messages available from stream '{}': {}", streamKey, e.getMessage());
        }
    }

    private void createConsumerGroupIfNotExists(String streamKey) {
        try {
            // Try to create the consumer group
            redisTemplate.opsForStream().createGroup(streamKey, consumerGroup);
            log.info("Created consumer group '{}' for stream '{}'", consumerGroup, streamKey);
        } catch (Exception e) {
            // Group might already exist or stream doesn't exist yet, both are fine
            log.debug("Consumer group '{}' setup for stream '{}': {}",
                    consumerGroup, streamKey, e.getMessage());

            // If stream doesn't exist, create it with a dummy message
            try {
                redisTemplate.opsForStream().createGroup(streamKey, ReadOffset.from("0"), consumerGroup);
                log.info("Created stream '{}' and consumer group '{}'", streamKey, consumerGroup);
            } catch (Exception ex) {
                log.trace("Stream and group may already exist: {}", ex.getMessage());
            }
        }
    }

    @FunctionalInterface
    private interface MessageHandler {
        void handle(Map<Object, Object> value) throws Exception;
    }

    private void handleUserEvent(Map<Object, Object> value) {
        try {
            // Convert map to clean Map<String, Object>
            log.debug("RAW Redis data: {}", value);

            Map<String, Object> cleanedValue = cleanMap(value);
            log.debug("CLEANED data: {}", cleanedValue);

            UserRegisteredEvent event = objectMapper.convertValue(cleanedValue, UserRegisteredEvent.class);
            log.debug("PARSED event: userId={}, email={}, firstName={}",
                    event.getUserId(), event.getEmail(), event.getFirstName());

            log.info("Processing user.registered event for user: {}", event.getUserId());

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
        } catch (Exception e) {
            log.error("Failed to handle user event: {}", e.getMessage(), e);
        }
    }

    private void handleAssessmentEvent(Map<Object, Object> value) {
        try {
            // Check if it's a SessionCompletedEvent or AssessmentPublishedEvent
            if (value.containsKey("sessionId")) {
                handleSessionCompleted(value);
            } else if (value.containsKey("assignedUsers")) {
                handleAssessmentPublished(value);
            }
        } catch (Exception e) {
            log.error("Failed to handle assessment event: {}", e.getMessage(), e);
        }
    }

    private void handleSessionCompleted(Map<Object, Object> value) {
        try {
            Map<String, Object> cleanedValue = cleanMap(value);
            SessionCompletedEvent event = objectMapper.convertValue(cleanedValue, SessionCompletedEvent.class);

            log.info("Processing session.completed event for user: {}", event.getUserId());

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
        } catch (Exception e) {
            log.error("Failed to handle session completed event: {}", e.getMessage(), e);
        }
    }

    private void handleAssessmentPublished(Map<Object, Object> value) {
        try {
            Map<String, Object> cleanedValue = cleanMap(value);
            AssessmentPublishedEvent event = objectMapper.convertValue(cleanedValue, AssessmentPublishedEvent.class);

            log.info("Processing assessment.published event for assessment: {}", event.getAssessmentId());

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
        } catch (Exception e) {
            log.error("Failed to handle assessment published event: {}", e.getMessage(), e);
        }
    }

    private void handleProctoringEvent(Map<Object, Object> value) {
        try {
            Map<String, Object> cleanedValue = cleanMap(value);
            ProctoringViolationEvent event = objectMapper.convertValue(cleanedValue, ProctoringViolationEvent.class);

            log.info("Processing proctoring.violation event for session: {}", event.getSessionId());

            Map<String, Object> data = new HashMap<>();
            data.put("username", event.getUsername());
            data.put("sessionId", event.getSessionId());
            data.put("violationType", event.getViolationType());
            data.put("timestamp", event.getTimestamp() != null ? event.getTimestamp().toString() : Instant.now().toString());
            data.put("severity", event.getSeverity());

            if (event.getProctorIds() != null && !event.getProctorIds().isEmpty()) {  // ← Added null check
                for (Integer proctorId : event.getProctorIds()) {
                    notificationService.processNotification(
                            "proctoring.violation",
                            proctorId,
                            null,
                            data,
                            List.of(NotificationChannel.SSE, NotificationChannel.EMAIL)
                    );
                }
            } else {
                log.warn("No proctor IDs found for proctoring violation event");
            }
        } catch (Exception e) {
            log.error("Failed to handle proctoring event: {}", e.getMessage(), e);
        }
    }

    /**
     * Clean map by converting all keys to strings and filtering out unwanted fields
     */
    private Map<String, Object> cleanMap(Map<Object, Object> original) {
        Map<String, Object> cleaned = new HashMap<>();
        Base64.Decoder decoder = Base64.getDecoder();

        for (Map.Entry<Object, Object> entry : original.entrySet()) {
            String key = entry.getKey().toString();
            if (!key.equals("init") && !key.startsWith("_")) {
                String raw = entry.getValue().toString();

                // decode all fields (or selectively per key)
                String decoded = new String(decoder.decode(raw), StandardCharsets.UTF_8);
                cleaned.put(key, decoded);
            }
        }
        return cleaned;
    }
}