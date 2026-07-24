package com.miniweverse.chat.membership;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MembershipPeriodRepository extends JpaRepository<MembershipPeriod, Long> {

    @Query("""
            SELECT p FROM MembershipPeriod p
            WHERE p.fanUserId = :fanUserId AND p.artistId = :artistId AND p.endedAt IS NULL
            """)
    Optional<MembershipPeriod> findOpenPeriod(@Param("fanUserId") Long fanUserId, @Param("artistId") Long artistId);

    /**
     * 아티스트가 방송(artist-message)을 보낼 때 알림을 fan-out할 대상 — 지금 이 방을
     * 활성 구독 중인 팬 전원(열린 기간이 있는 fanUserId). 같은 팬이 과거에 재구독해 기간이
     * 여러 개였어도 열린 기간은 항상 최대 1개(uk_membership_periods_open)라 중복 걱정 없다.
     */
    @Query("""
            SELECT p.fanUserId FROM MembershipPeriod p
            WHERE p.artistId = :artistId AND p.endedAt IS NULL
            """)
    List<Long> findFanUserIdsWithOpenPeriod(@Param("artistId") Long artistId);
}
