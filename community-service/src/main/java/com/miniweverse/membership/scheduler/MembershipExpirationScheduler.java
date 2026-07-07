package com.miniweverse.membership.scheduler;

import com.miniweverse.membership.service.MembershipService;
import java.time.LocalDateTime;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매일 자정, 만료일이 지난 ACTIVE 구독을 EXPIRED로 전환한다.
 */
@Component
public class MembershipExpirationScheduler {

    private final MembershipService membershipService;

    public MembershipExpirationScheduler(MembershipService membershipService) {
        this.membershipService = membershipService;
    }

    @Scheduled(cron = "0 0 0 * * *")
    public void expireOverdueMemberships() {
        membershipService.expireOverdueMemberships(LocalDateTime.now());
    }
}
