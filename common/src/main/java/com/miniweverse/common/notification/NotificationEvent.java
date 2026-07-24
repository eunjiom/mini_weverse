package com.miniweverse.common.notification;

import java.time.LocalDateTime;

/**
 * Kafka notification-events 토픽에 실리는 메시지 본문. community-service/chat-service가
 * 발행하고 notification-service가 그대로 역직렬화해서 소비한다 — 두 쪽이 완전히 동일한
 * 계약을 공유해야 하므로 common 모듈에 둔다.
 *
 * @param eventId     발행 서비스가 만드는 전역 고유 ID(UUID). 각 서비스의 아웃박스 PK는
 *                    서비스마다 로컬 auto-increment라 서로 겹칠 수 있어 그대로 못 쓴다.
 * @param targetUserId 알림을 받을 단일 유저. fan-out(여러 명에게 가는 알림)은 발행 측에서
 *                    수신자 수만큼 이벤트를 미리 쪼개서 만들기 때문에, 여기서는 항상 1명이다.
 */
public record NotificationEvent(
        String eventId,
        NotificationType type,
        Long targetUserId,
        String title,
        String message,
        LocalDateTime occurredAt
) {
}
