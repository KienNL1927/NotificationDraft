// NotificationSentEvent.java
package com.example.notificationservice.event.outbound;

import com.example.notificationservice.event.BaseEvent;
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
public class NotificationSentEvent extends BaseEvent {
    private Integer notificationId;
    private Integer recipientId;
    private String channel;
    private String type;
    private String status;
    private String deliveryTime;
}
