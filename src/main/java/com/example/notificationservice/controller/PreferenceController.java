package com.example.notificationservice.controller;

import com.example.notificationservice.config.CasdoorAuthenticationContext;
import com.example.notificationservice.dto.PreferenceDto;
import com.example.notificationservice.service.PreferenceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Slf4j
public class PreferenceController {

    private final PreferenceService preferenceService;
    private final CasdoorAuthenticationContext authContext;

    @GetMapping("/{userId}/preferences")
    @PreAuthorize("hasRole('ADMIN') or #userId == authentication.principal")
    public ResponseEntity<PreferenceDto> getUserPreferences(@PathVariable Integer userId) {

        // Verify user is accessing their own preferences or is admin
        Integer currentUserId = authContext.getCurrentUserId().orElse(null);
        boolean isAdmin = authContext.isAdmin();

        if (!isAdmin && !userId.equals(currentUserId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        log.info("Fetching preferences for user: {}", userId);
        return preferenceService.getUserPreferences(userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{userId}/preferences")
    @PreAuthorize("hasRole('ADMIN') or #userId == authentication.principal")
    public ResponseEntity<PreferenceDto> updateUserPreferences(
            @PathVariable Integer userId,
            @Valid @RequestBody PreferenceDto preferenceDto) {

        // Verify user is updating their own preferences or is admin
        Integer currentUserId = authContext.getCurrentUserId().orElse(null);
        boolean isAdmin = authContext.isAdmin();

        if (!isAdmin && !userId.equals(currentUserId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        log.info("Updating preferences for user: {}", userId);
        preferenceDto.setUserId(userId);
        PreferenceDto updated = preferenceService.updateUserPreferences(preferenceDto);
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/{userId}/preferences")
    @PreAuthorize("hasRole('ADMIN') or #userId == authentication.principal")
    public ResponseEntity<PreferenceDto> createUserPreferences(
            @PathVariable Integer userId,
            @Valid @RequestBody PreferenceDto preferenceDto) {

        // Verify user is creating their own preferences or is admin
        Integer currentUserId = authContext.getCurrentUserId().orElse(null);
        boolean isAdmin = authContext.isAdmin();

        if (!isAdmin && !userId.equals(currentUserId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        log.info("Creating preferences for user: {}", userId);
        preferenceDto.setUserId(userId);
        PreferenceDto created = preferenceService.createUserPreferences(preferenceDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}