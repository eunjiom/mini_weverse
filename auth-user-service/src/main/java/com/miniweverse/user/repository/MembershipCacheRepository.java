package com.miniweverse.user.repository;

import com.miniweverse.user.entity.MembershipCache;
import com.miniweverse.user.entity.User;
import com.miniweverse.user.enums.MembershipStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MembershipCacheRepository extends JpaRepository<MembershipCache, Long> {

    Optional<MembershipCache> findBySubscriberAndArtist(User subscriber, User artist);

    List<MembershipCache> findByStatusAndExpiresAtBefore(MembershipStatus status, LocalDateTime time);
}
