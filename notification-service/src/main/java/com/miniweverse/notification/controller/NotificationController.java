package com.miniweverse.notification.controller;

import com.miniweverse.notification.dto.NotificationListResponse;
import com.miniweverse.notification.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class NotificationController {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/notifications")
    public ResponseEntity<NotificationListResponse> getNotifications(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) Integer limit
    ) {
        int resolvedLimit = Math.min(Math.max(limit != null ? limit : DEFAULT_LIMIT, 1), MAX_LIMIT);
        return ResponseEntity.ok(notificationService.getNotifications(userId, resolvedLimit));
    }

    @PostMapping("/notifications/read")
    public ResponseEntity<Void> markAllRead(@AuthenticationPrincipal Long userId) {
        notificationService.markAllRead(userId);
        return ResponseEntity.noContent().build();
    }
}
