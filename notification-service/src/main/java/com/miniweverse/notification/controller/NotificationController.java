package com.miniweverse.notification.controller;

import com.miniweverse.notification.dto.NotificationListResponse;
import com.miniweverse.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "알림", description = "멤버십/채팅 이벤트 기반 알림 조회 및 읽음 처리")
@SecurityRequirement(name = "bearerAuth")
public class NotificationController {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Operation(summary = "알림 목록 조회", description = "최근 알림을 최신순으로 조회합니다. limit 미지정 시 20건, 최대 50건까지 조회 가능합니다.")
    @GetMapping("/notifications")
    public ResponseEntity<NotificationListResponse> getNotifications(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) Integer limit
    ) {
        int resolvedLimit = Math.min(Math.max(limit != null ? limit : DEFAULT_LIMIT, 1), MAX_LIMIT);
        return ResponseEntity.ok(notificationService.getNotifications(userId, resolvedLimit));
    }

    @Operation(summary = "알림 전체 읽음 처리")
    @PostMapping("/notifications/read")
    public ResponseEntity<Void> markAllRead(@AuthenticationPrincipal Long userId) {
        notificationService.markAllRead(userId);
        return ResponseEntity.noContent().build();
    }
}
