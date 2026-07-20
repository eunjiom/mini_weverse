package com.miniweverse.membership.outbox;

import com.miniweverse.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
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

    /**
     * chat-service의 MembershipPeriod 경계 시각 — ACTIVATED면 새로 연 기간의 시작 시각(그냥 연장인
     * 경우엔 새 기간을 안 여니까 null), EXPIRED면 방금 닫은 기간의 종료 시각(항상 값 있음).
     * 아웃박스 폴링 지연(최대 5초) 동안에도 chat-service가 정확한 경계 시각을 갖도록, chat-service
     * 수신 시각이 아니라 이 이벤트가 발생한 시각을 그대로 실어보낸다.
     */
    @Column(name = "period_boundary_at")
    private LocalDateTime periodBoundaryAt;

    private MembershipOutboxEvent(MembershipOutboxEventType eventType, Long fanUserId, Long artistId, LocalDateTime periodBoundaryAt) {
        this.eventType = eventType;
        this.fanUserId = fanUserId;
        this.artistId = artistId;
        this.periodBoundaryAt = periodBoundaryAt;
        this.status = OutboxEventStatus.PENDING;
        this.attemptCount = 0;
    }

    public static MembershipOutboxEvent activated(Long fanUserId, Long artistId, LocalDateTime newPeriodStartedAt) {
        return new MembershipOutboxEvent(MembershipOutboxEventType.MEMBERSHIP_ACTIVATED, fanUserId, artistId, newPeriodStartedAt);
    }

    public static MembershipOutboxEvent expired(Long fanUserId, Long artistId, LocalDateTime periodEndedAt) {
        return new MembershipOutboxEvent(MembershipOutboxEventType.MEMBERSHIP_EXPIRED, fanUserId, artistId, periodEndedAt);
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
