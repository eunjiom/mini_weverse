package com.miniweverse.notification.dto;

import java.util.List;

public record NotificationListResponse(List<NotificationResponse> notifications, long unreadCount) {
}
