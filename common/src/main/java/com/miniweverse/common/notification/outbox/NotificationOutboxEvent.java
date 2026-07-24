package com.miniweverse.common.notification.outbox;

import com.miniweverse.common.entity.BaseTimeEntity;
import com.miniweverse.common.notification.NotificationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * community-service/chat-service가 알림 트리거(팔로우/멤버십/게시글/댓글/채팅메시지)가 발생할 때마다
 * 자기 트랜잭션 안에서 함께 저장하는 아웃박스. NotificationOutboxPublisher가 폴링해서 Kafka로
 * 발행한다. 두 서비스 다 완전히 동일한 구조·처리 로직을 쓰므로 common 모듈에 둔다(BaseTimeEntity와
 * 같은 이유). 각 서비스는 자기 DB에 이 테이블을 각자 소유한다(서비스 간 공유 테이블 아님).
 *
 * 여러 명에게 가는 알림(fan-out, 예: 새 게시글 → 팔로워 전원)은 발행 서비스가 수신자 수만큼
 * 이 row를 미리 여러 개 만들어서 처리한다 — notification-service는 항상 "받는 사람 1명짜리"
 * 이벤트만 소비하면 된다.
 */
@Entity
@Table(name = "notification_outbox_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationOutboxEvent extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 전역 고유 식별자. 이 엔티티의 PK는 서비스마다(community/chat) 로컬 auto-increment라
     * 서로 겹칠 수 있어, Kafka 메시지·notification-service 쪽 참조에는 이 UUID를 대신 싣는다.
     */
    @Column(name = "event_id", nullable = false, unique = true)
    private String eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false)
    private NotificationType type;

    @Column(name = "target_user_id", nullable = false)
    private Long targetUserId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationOutboxStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    private NotificationOutboxEvent(NotificationType type, Long targetUserId, String title, String message) {
        this.eventId = UUID.randomUUID().toString();
        this.type = type;
        this.targetUserId = targetUserId;
        this.title = title;
        this.message = message;
        this.status = NotificationOutboxStatus.PENDING;
        this.attemptCount = 0;
    }

    public static NotificationOutboxEvent of(NotificationType type, Long targetUserId, String title, String message) {
        return new NotificationOutboxEvent(type, targetUserId, title, message);
    }

    public void markSent() {
        this.status = NotificationOutboxStatus.SENT;
    }

    /** maxAttempts에 도달하면 더 이상 자동 재시도 대상에서 빠진다(DEAD) — 운영 확인이 필요하다. */
    public void recordFailure(int maxAttempts) {
        this.attemptCount++;
        if (this.attemptCount >= maxAttempts) {
            this.status = NotificationOutboxStatus.DEAD;
        }
    }
}
