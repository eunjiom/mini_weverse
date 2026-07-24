package com.miniweverse.notification.dto;

import com.miniweverse.common.notification.NotificationEvent;
import com.miniweverse.common.notification.NotificationType;
import java.time.LocalDateTime;

public record NotificationResponse(
        NotificationType type,
        String title,
        String message,
        LocalDateTime occurredAt,
        boolean read
) {
    public static NotificationResponse of(NotificationEvent event, boolean read) {
        return new NotificationResponse(event.type(), event.title(), event.message(), event.occurredAt(), read);
    }
}
