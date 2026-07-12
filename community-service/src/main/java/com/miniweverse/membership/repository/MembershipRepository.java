package com.miniweverse.membership.repository;

import com.miniweverse.membership.entity.Membership;
import com.miniweverse.user.entity.User;
import com.miniweverse.user.enums.MembershipStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface MembershipRepository extends JpaRepository<Membership, Long> {

    /**
     * 같은 (subscriber, artist) row에 대한 동시 구독/갱신 요청을 직렬화하기 위해 비관적 락을 건다.
     * 먼저 들어온 트랜잭션이 커밋될 때까지 나중 요청은 대기했다가 갱신 후 상태를 이어받는다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Membership> findBySubscriberAndArtist(User subscriber, User artist);

    List<Membership> findByStatusAndExpiresAtBefore(MembershipStatus status, LocalDateTime time);
}
