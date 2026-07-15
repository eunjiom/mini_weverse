package com.miniweverse.membership.scheduler;

import com.miniweverse.membership.client.ChatServiceMembershipNotifier;
import com.miniweverse.membership.entity.Membership;
import com.miniweverse.membership.service.MembershipService;
import com.miniweverse.user.entity.ArtistProfile;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매일 자정, 만료일이 지난 ACTIVE 구독을 EXPIRED로 전환한다.
 */
@Component
public class MembershipExpirationScheduler {

    private final MembershipService membershipService;
    private final ChatServiceMembershipNotifier chatServiceMembershipNotifier;

    public MembershipExpirationScheduler(
            MembershipService membershipService,
            ChatServiceMembershipNotifier chatServiceMembershipNotifier
    ) {
        this.membershipService = membershipService;
        this.chatServiceMembershipNotifier = chatServiceMembershipNotifier;
    }

    @Scheduled(cron = "0 0 0 * * *")
    public void expireOverdueMemberships() {
        List<Membership> expired = membershipService.expireOverdueMemberships(LocalDateTime.now());
        // 트랜잭션(커밋) 완료 후에 알림 — chat-service가 실시간 메시지 전달을 즉시 끊을 수 있게.
        for (Membership membership : expired) {
            ArtistProfile artist = membership.getArtist();
            if (artist != null) {
                chatServiceMembershipNotifier.notifyExpired(membership.getSubscriber().getId(), artist.getId());
            }
        }
    }
}
