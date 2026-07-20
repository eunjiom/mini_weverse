package com.miniweverse.chat.membership;

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
}
