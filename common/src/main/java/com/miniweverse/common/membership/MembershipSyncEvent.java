package com.miniweverse.common.membership;

import java.time.LocalDateTime;

/**
 * Kafka membership-sync-events 토픽에 실리는 메시지 본문. community-service가 발행하고
 * chat-service가 그대로 역직렬화해서 소비한다 — 두 쪽이 완전히 동일한 계약을 공유해야 하므로
 * common 모듈에 둔다(NotificationEvent와 동일한 이유).
 *
 * @param periodBoundaryAt ACTIVATED면 새로 연 기간의 시작 시각(그냥 연장인 경우엔 null),
 *                         EXPIRED면 방금 닫은 기간의 종료 시각(항상 값 있음)
 */
public record MembershipSyncEvent(
        MembershipSyncEventType eventType,
        Long fanUserId,
        Long artistId,
        LocalDateTime periodBoundaryAt
) {
}
