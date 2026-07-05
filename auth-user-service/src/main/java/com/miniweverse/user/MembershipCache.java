package com.miniweverse.user;

import com.miniweverse.common.BaseTimeEntity;
import com.miniweverse.exception.NotArtistException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Membership 서비스가 소유한 구독 상태의 읽기 전용 캐시.
 * 실제 결제/갱신 로직(Kafka 이벤트 수신 등)은 이후 PR에서 다룬다.
 */
@Entity
@Table(
        name = "membership_caches",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_membership_caches_subscriber_artist",
                columnNames = {"subscriber_id", "artist_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MembershipCache extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscriber_id", nullable = false)
    private User subscriber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "artist_id", nullable = false)
    private User artist;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MembershipStatus status;

    private LocalDateTime expiresAt;

    private MembershipCache(User subscriber, User artist, MembershipStatus status, LocalDateTime expiresAt) {
        this.subscriber = subscriber;
        this.artist = artist;
        this.status = status;
        this.expiresAt = expiresAt;
    }

    public static MembershipCache create(User subscriber, User artist, MembershipStatus status, LocalDateTime expiresAt) {
        if (artist.getRole() != Role.ARTIST) {
            throw new NotArtistException();
        }
        return new MembershipCache(subscriber, artist, status, expiresAt);
    }
}
