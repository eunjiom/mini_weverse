package com.miniweverse.membership.entity;

import com.miniweverse.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 구독이 실제로 활성이었던 기간 하나. 재구독(공백 있었던 갱신)마다 새 row가 열리고, 그 사이 그냥
 * 연장(공백 없는 갱신)한 건 기존에 열려있던 기간을 그대로 이어간다 — chat-service가 "이 팬이 이
 * 메시지가 오간 시점에 실제로 구독 중이었는지"를 판단할 때 이 이력을 근거로 쓴다.
 */
@Entity
@Table(name = "membership_periods")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MembershipPeriod extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "membership_id", nullable = false)
    private Membership membership;

    @Column(nullable = false)
    private LocalDateTime startedAt;

    /** null이면 아직 진행 중인(만료 처리 안 된) 기간. */
    private LocalDateTime endedAt;

    private MembershipPeriod(Membership membership, LocalDateTime startedAt) {
        this.membership = membership;
        this.startedAt = startedAt;
    }

    public static MembershipPeriod start(Membership membership, LocalDateTime startedAt) {
        return new MembershipPeriod(membership, startedAt);
    }

    public void close(LocalDateTime endedAt) {
        this.endedAt = endedAt;
    }
}
