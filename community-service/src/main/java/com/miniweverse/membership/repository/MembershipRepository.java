package com.miniweverse.membership.repository;

import com.miniweverse.membership.entity.Membership;
import com.miniweverse.user.entity.ArtistProfile;
import com.miniweverse.user.entity.User;
import com.miniweverse.user.enums.MembershipStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MembershipRepository extends JpaRepository<Membership, Long> {

    /**
     * 같은 (subscriber, artist) row에 대한 동시 구독/갱신 요청을 직렬화하기 위해 비관적 락을 건다.
     * 먼저 들어온 트랜잭션이 커밋될 때까지 나중 요청은 대기했다가 갱신 후 상태를 이어받는다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Membership> findBySubscriberAndArtist(User subscriber, ArtistProfile artist);

    /**
     * 같은 구독 건에 대한 동시 취소 요청을 직렬화하기 위해 비관적 락을 건다.
     * 락 없이 조회하면 두 요청이 모두 "아직 취소 안 됨" 상태를 읽고 통과해버릴 수 있다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Membership m WHERE m.id = :id")
    Optional<Membership> findByIdForUpdate(@Param("id") Long id);

    List<Membership> findByStatusAndExpiresAtBefore(MembershipStatus status, LocalDateTime time);

    /**
     * open-in-view: false라 트랜잭션 밖(컨트롤러 DTO 매핑)에서 artist에 접근하려면
     * JOIN FETCH로 미리 로딩해야 한다 (PostRepository와 동일한 이유).
     * LEFT JOIN이다 — INNER JOIN이면 구독한 아티스트가 탈퇴(User.deletedAt)했을 때
     * User의 {@code @SQLRestriction} 때문에 그 구독 이력이 통째로 걸러진다.
     */
    @Query("SELECT m FROM Membership m LEFT JOIN FETCH m.artist WHERE m.subscriber.id = :subscriberId ORDER BY m.createdAt DESC")
    List<Membership> findBySubscriberIdWithArtist(@Param("subscriberId") Long subscriberId);
}
