package com.miniweverse.membership.scheduler;

import com.miniweverse.membership.service.MembershipService;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매일 자정, 만료일이 지난 ACTIVE 구독을 EXPIRED로 전환한다.
 * chat-service 만료 알림은 MembershipService.expireOne()이 각 건의 트랜잭션 안에서
 * 아웃박스에 적재하고, MembershipOutboxPublisher가 별도로 전송/재시도한다.
 */
@Component
public class MembershipExpirationScheduler {

    private static final Logger log = LoggerFactory.getLogger(MembershipExpirationScheduler.class);

    private final MembershipService membershipService;

    public MembershipExpirationScheduler(MembershipService membershipService) {
        this.membershipService = membershipService;
    }

    @Scheduled(cron = "0 0 0 * * *")
    public void expireOverdueMemberships() {
        LocalDateTime now = LocalDateTime.now();
        for (Long membershipId : membershipService.findOverdueMembershipIds(now)) {
            try {
                membershipService.expireOne(membershipId, now);
            } catch (ObjectOptimisticLockingFailureException e) {
                // 조회 이후 유저가 먼저 갱신을 커밋한 경우 — 더 이상 만료 대상이 아니므로 건너뜀
            } catch (Exception e) {
                // 한 건에서 예상 못 한 에러가 나도 배치 전체를 중단시키지 않고, 이 건만 건너뛰고 계속한다.
                log.error("멤버십 만료 처리 실패, membershipId={}", membershipId, e);
            }
        }
    }
}
