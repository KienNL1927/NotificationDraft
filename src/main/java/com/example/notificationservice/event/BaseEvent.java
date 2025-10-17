// Base Event Class
package com.example.notificationservice.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public abstract class BaseEvent {
    private String eventId;
    private LocalDateTime timestamp;

    public BaseEvent init() {
        this.eventId = UUID.randomUUID().toString();
        this.timestamp = LocalDateTime.now();
        return this;
    }
}
