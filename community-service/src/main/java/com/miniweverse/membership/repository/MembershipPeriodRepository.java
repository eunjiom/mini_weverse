package com.miniweverse.membership.repository;

import com.miniweverse.membership.entity.MembershipPeriod;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MembershipPeriodRepository extends JpaRepository<MembershipPeriod, Long> {

    @Query("SELECT p FROM MembershipPeriod p WHERE p.membership.id = :membershipId AND p.endedAt IS NULL")
    Optional<MembershipPeriod> findOpenPeriod(@Param("membershipId") Long membershipId);
}
