package com.miniweverse.membership.outbox;

import com.miniweverse.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * community-service가 chat-service에 보내야 하는 멤버십 변경 알림을 트랜잭션 안전하게 적재해두는
 * 아웃박스. 멤버십 상태 변경(subscribe/expire)과 같은 트랜잭션에서 함께 저장되므로, chat-service
 * 호출이 그 순간 실패하거나 서버가 죽어도 이 이벤트는 유실되지 않고 MembershipOutboxPublisher가
 * 별도로 재시도한다.
 */
@Entity
@Table(name = "membership_outbox_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MembershipOutboxEvent extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private MembershipOutboxEventType eventType;

    @Column(name = "fan_user_id", nullable = false)
    private Long fanUserId;

    @Column(name = "artist_id", nullable = false)
    private Long artistId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxEventStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    private MembershipOutboxEvent(MembershipOutboxEventType eventType, Long fanUserId, Long artistId) {
        this.eventType = eventType;
        this.fanUserId = fanUserId;
        this.artistId = artistId;
        this.status = OutboxEventStatus.PENDING;
        this.attemptCount = 0;
    }

    public static MembershipOutboxEvent activated(Long fanUserId, Long artistId) {
        return new MembershipOutboxEvent(MembershipOutboxEventType.MEMBERSHIP_ACTIVATED, fanUserId, artistId);
    }

    public static MembershipOutboxEvent expired(Long fanUserId, Long artistId) {
        return new MembershipOutboxEvent(MembershipOutboxEventType.MEMBERSHIP_EXPIRED, fanUserId, artistId);
    }

    public void markSent() {
        this.status = OutboxEventStatus.SENT;
    }

    /** maxAttempts에 도달하면 더 이상 자동 재시도 대상에서 빠진다(DEAD) — 운영 확인이 필요하다. */
    public void recordFailure(int maxAttempts) {
        this.attemptCount++;
        if (this.attemptCount >= maxAttempts) {
            this.status = OutboxEventStatus.DEAD;
        }
    }
}
