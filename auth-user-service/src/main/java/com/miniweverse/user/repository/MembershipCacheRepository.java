package com.miniweverse.user.repository;

import com.miniweverse.user.MembershipCache;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MembershipCacheRepository extends JpaRepository<MembershipCache, Long> {
}
