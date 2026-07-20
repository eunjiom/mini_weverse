package com.miniweverse.chat.membership;

import com.miniweverse.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * community-service MembershipPeriod의 chat-service 쪽 사본. 서비스가 분리돼 있어 FK/직접 조회가
 * 안 되므로, community-service가 구독 시작/종료 때마다 push하는 시각을 그대로 저장해둔다
 * (ChatMessageRepository.findVisibleMessages가 이 이력으로 "그 시점에 구독 중이었는지" 판단).
 */
@Entity
@Table(
        name = "membership_periods",
        indexes = @Index(name = "idx_membership_periods_fan_artist", columnList = "fan_user_id, artist_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MembershipPeriod extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fan_user_id", nullable = false)
    private Long fanUserId;

    @Column(name = "artist_id", nullable = false)
    private Long artistId;

    @Column(nullable = false)
    private LocalDateTime startedAt;

    /** null이면 아직 진행 중인 기간. */
    private LocalDateTime endedAt;

    private MembershipPeriod(Long fanUserId, Long artistId, LocalDateTime startedAt) {
        this.fanUserId = fanUserId;
        this.artistId = artistId;
        this.startedAt = startedAt;
    }

    public static MembershipPeriod start(Long fanUserId, Long artistId, LocalDateTime startedAt) {
        return new MembershipPeriod(fanUserId, artistId, startedAt);
    }

    public void close(LocalDateTime endedAt) {
        this.endedAt = endedAt;
    }
}
