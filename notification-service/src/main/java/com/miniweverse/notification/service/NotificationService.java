package com.miniweverse.notification.service;

import com.miniweverse.common.notification.NotificationEvent;
import com.miniweverse.notification.dto.NotificationListResponse;
import com.miniweverse.notification.dto.NotificationResponse;
import com.miniweverse.notification.repository.NotificationRedisRepository;
import java.time.ZoneId;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private final NotificationRedisRepository notificationRedisRepository;

    public NotificationService(NotificationRedisRepository notificationRedisRepository) {
        this.notificationRedisRepository = notificationRedisRepository;
    }

    public NotificationListResponse getNotifications(Long userId, int limit) {
        Long lastReadAt = notificationRedisRepository.getLastReadAt(userId);
        long unreadCount = notificationRedisRepository.countUnread(userId);

        var responses = notificationRedisRepository.findRecent(userId, limit).stream()
                .map(event -> NotificationResponse.of(event, isRead(event, lastReadAt)))
                .toList();
        return new NotificationListResponse(responses, unreadCount);
    }

    public void markAllRead(Long userId) {
        notificationRedisRepository.markAllRead(userId);
    }

    private boolean isRead(NotificationEvent event, Long lastReadAt) {
        if (lastReadAt == null) {
            return false;
        }
        long occurredAtMillis = event.occurredAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        return occurredAtMillis <= lastReadAt;
    }
}
