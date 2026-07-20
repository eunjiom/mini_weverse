package com.miniweverse.membership.entity;

import com.miniweverse.common.entity.BaseTimeEntity;
import com.miniweverse.exception.AuthUserExceptions.InvalidRequestException;
import com.miniweverse.exception.AuthUserExceptions.NotArtistException;
import com.miniweverse.user.entity.ArtistProfile;
import com.miniweverse.user.entity.User;
import java.util.Objects;
import com.miniweverse.user.enums.MembershipStatus;
import com.miniweverse.common.security.jwt.Role;
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
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;

/**
 * 아티스트 멤버십(구독) 상태. 결제 연동 없이 이 서비스가 구독/갱신/취소/만료를 직접 소유한다.
 */
@Entity
@Table(
        name = "memberships",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_memberships_subscriber_artist",
                columnNames = {"subscriber_id", "artist_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Membership extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscriber_id", nullable = false)
    private User subscriber;

    /**
     * artist는 User가 아니라 ArtistProfile을 가리킨다(Follow.artist와 동일한 이유).
     * ArtistProfile(또는 그 소유주 User)이 탈퇴/삭제돼도 구독 이력 자체는 남아있어야 하므로, User의
     * {@code @SQLRestriction}에 걸려 로딩이 안 되는 경우 예외 대신 null로 취급한다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "artist_id", nullable = false)
    @NotFound(action = NotFoundAction.IGNORE)
    private ArtistProfile artist;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MembershipStatus status;

    private LocalDateTime expiresAt;

    private LocalDateTime cancelledAt;

    /**
     * 자정 배치의 만료 처리와 유저의 갱신 요청이 같은 row를 거의 동시에 건드리는 극히 드문 경우를
     * 대비한 낙관적 락. 배치가 만료 처리하려는 순간 그 사이 갱신이 먼저 커밋되면, 배치 쪽 저장이
     * 버전 충돌로 실패하고 그 건은 조용히 건너뛴다(MembershipService.expireOne 참고).
     */
    @Version
    @Column(nullable = false, columnDefinition = "bigint default 0")
    private Long version;

    private Membership(User subscriber, ArtistProfile artist, MembershipStatus status, LocalDateTime expiresAt) {
        this.subscriber = subscriber;
        this.artist = artist;
        this.status = status;
        this.expiresAt = expiresAt;
    }

    public static Membership create(User subscriber, ArtistProfile artist, MembershipStatus status, LocalDateTime expiresAt) {
        if (subscriber == null || artist == null) {
            throw new InvalidRequestException("subscriber와 artist는 필수입니다.");
        }
        User artistUser = artist.requireOwner();
        boolean isSelfSubscription = subscriber == artistUser
                || (subscriber.getId() != null && Objects.equals(subscriber.getId(), artistUser.getId()));
        if (isSelfSubscription) {
            throw new InvalidRequestException("자기 자신을 구독할 수 없습니다.");
        }
        if (artistUser.getRole() != Role.ARTIST) {
            throw new NotArtistException();
        }
        return new Membership(subscriber, artist, status, expiresAt);
    }

    /**
     * 구독 갱신. 이미 만료됐으면 갱신 시점부터 1개월, 아직 유효(ACTIVE)하면 기존 만료일에 1개월을 이어붙인다.
     * 재구독하면 취소 여부는 무효화된다.
     *
     * @return 공백(만료) 뒤에 다시 구독해서 새 {@link MembershipPeriod}를 열어야 하면 true,
     *         만료 전에 그냥 연장한 거면(기존 기간이 계속 이어짐) false
     */
    public boolean renew(LocalDateTime now) {
        boolean alreadyExpired = status == MembershipStatus.EXPIRED || expiresAt == null || expiresAt.isBefore(now);
        this.expiresAt = alreadyExpired ? now.plusMonths(1) : expiresAt.plusMonths(1);
        this.status = MembershipStatus.ACTIVE;
        this.cancelledAt = null;
        return alreadyExpired;
    }

    public void expire() {
        this.status = MembershipStatus.EXPIRED;
    }

    public void cancel() {
        if (status != MembershipStatus.ACTIVE) {
            throw new InvalidRequestException("활성 구독만 취소할 수 있습니다.");
        }
        if (cancelledAt != null) {
            throw new InvalidRequestException("이미 취소된 구독입니다.");
        }
        this.cancelledAt = LocalDateTime.now();
    }
}
