package com.example.notificationservice.util;

import com.example.notificationservice.event.inbound.AssessmentPublishedEvent;
import com.example.notificationservice.event.inbound.ProctoringViolationEvent;
import com.example.notificationservice.event.inbound.SessionCompletedEvent;
import com.example.notificationservice.event.inbound.UserRegisteredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Scanner;

/**
 * Test data generator for development and testing
 * Only active when 'test-data' profile is enabled
 *
 * Usage: Add --spring.profiles.active=test-data when running the application
 */
@Component
@Profile("test-data")
@RequiredArgsConstructor
@Slf4j
public class TestDataGenerator implements CommandLineRunner {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topics.inbound.user-events}")
    private String userEventsTopic;

    @Value("${app.kafka.topics.inbound.assessment-events}")
    private String assessmentEventsTopic;

    @Value("${app.kafka.topics.inbound.proctoring-events}")
    private String proctoringEventsTopic;

    @Override
    public void run(String... args) throws Exception {
        Scanner scanner = new Scanner(System.in);
        boolean running = true;

        log.info("=== Test Data Generator Started ===");
        log.info("This utility helps you generate test events for the notification service");

        while (running) {
            printMenu();
            String choice = scanner.nextLine();

            switch (choice) {
                case "1" -> generateUserRegisteredEvent();
                case "2" -> generateSessionCompletedEvent();
                case "3" -> generateProctoringViolationEvent();
                case "4" -> generateAssessmentPublishedEvent();
                case "5" -> generateBulkEvents();
                case "0" -> {
                    running = false;
                    log.info("Test Data Generator stopped");
                }
                default -> log.warn("Invalid choice. Please try again.");
            }
        }
    }

    private void printMenu() {
        System.out.println("\n=== Test Data Generator Menu ===");
        System.out.println("1. Generate User Registration Event");
        System.out.println("2. Generate Session Completion Event");
        System.out.println("3. Generate Proctoring Violation Event");
        System.out.println("4. Generate Assessment Published Event");
        System.out.println("5. Generate Bulk Events (5 of each type)");
        System.out.println("0. Exit");
        System.out.print("Choose an option: ");
    }

    private void generateUserRegisteredEvent() {
        UserRegisteredEvent event = UserRegisteredEvent.builder()
                .userId(123)
                .username("john.doe")
                .email("john.doe@example.com")
                .firstName("John")
                .lastName("Doe")
                .build();
        event.init();

        kafkaTemplate.send(userEventsTopic, event);
        log.info("✓ User Registration Event sent for user: {}", event.getUsername());
    }

    private void generateSessionCompletedEvent() {
        SessionCompletedEvent event = SessionCompletedEvent.builder()
                .userId(123)
                .username("john.doe")
                .email("john.doe@example.com")
                .sessionId("SESSION-" + System.currentTimeMillis())
                .assessmentName("Java Programming Assessment")
                .completionTime("45 minutes")
                .score(85.5)
                .status("PASSED")
                .build();
        event.init();

        kafkaTemplate.send(assessmentEventsTopic, event);
        log.info("✓ Session Completion Event sent for session: {}", event.getSessionId());
    }

    private void generateProctoringViolationEvent() {
        ProctoringViolationEvent event = ProctoringViolationEvent.builder()
                .userId(123)
                .username("john.doe")
                .sessionId("SESSION-" + System.currentTimeMillis())
                .violationType("MULTIPLE_FACES_DETECTED")
                .severity("HIGH")
                .proctorIds(List.of(1, 2, 3))
                .build();
        event.init();

        kafkaTemplate.send(proctoringEventsTopic, event);
        log.info("✓ Proctoring Violation Event sent for session: {}", event.getSessionId());
    }

    private void generateAssessmentPublishedEvent() {
        AssessmentPublishedEvent event = AssessmentPublishedEvent.builder()
                .assessmentId("ASSESS-" + System.currentTimeMillis())
                .assessmentName("Spring Boot Advanced")
                .duration(120)
                .dueDate(Instant.now().plusSeconds(604800).toString())
                .assignedUsers(List.of(
                        AssessmentPublishedEvent.UserInfo.builder()
                                .userId(123)
                                .username("john.doe")
                                .email("john.doe@example.com")
                                .build(),
                        AssessmentPublishedEvent.UserInfo.builder()
                                .userId(124)
                                .username("jane.smith")
                                .email("jane.smith@example.com")
                                .build()
                ))
                .build();
        event.init();

        kafkaTemplate.send(assessmentEventsTopic, event);
        log.info("✓ Assessment Published Event sent for assessment: {}", event.getAssessmentName());
    }

    private void generateBulkEvents() {
        log.info("Generating bulk events...");

        // Generate 5 user registrations
        for (int i = 1; i <= 5; i++) {
            UserRegisteredEvent event = UserRegisteredEvent.builder()
                    .userId(100 + i)
                    .username("user" + i)
                    .email("user" + i + "@example.com")
                    .firstName("User")
                    .lastName("Number" + i)
                    .build();
            event.init();
            kafkaTemplate.send(userEventsTopic, event);
        }
        log.info("✓ 5 User Registration Events sent");

        // Generate 5 session completions
        for (int i = 1; i <= 5; i++) {
            SessionCompletedEvent event = SessionCompletedEvent.builder()
                    .userId(100 + i)
                    .username("user" + i)
                    .email("user" + i + "@example.com")
                    .sessionId("BULK-SESSION-" + i)
                    .assessmentName("Assessment " + i)
                    .completionTime((30 + i * 5) + " minutes")
                    .score(70.0 + i * 5)
                    .status(i % 2 == 0 ? "PASSED" : "FAILED")
                    .build();
            event.init();
            kafkaTemplate.send(assessmentEventsTopic, event);
        }
        log.info("✓ 5 Session Completion Events sent");

        log.info("✓ Bulk events generation completed!");
    }
}