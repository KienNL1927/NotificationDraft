// BulkNotificationCompletedEvent.java
package com.example.notificationservice.event.outbound;

import com.example.notificationservice.event.BaseEvent;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class BulkNotificationCompletedEvent extends BaseEvent {
    private String batchId;
    private Integer totalRecipients;
    private Integer successfulSent;
    private Integer failedSent;
    private String notificationType;
}