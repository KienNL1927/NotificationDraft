// Base Event Class
package com.example.notificationservice.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.UUID;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class BaseEvent {
    private String eventId;
    private Instant timestamp;

    public BaseEvent init() {
        this.eventId = UUID.randomUUID().toString();
        this.timestamp = Instant.now();
        return this;
    }
}
