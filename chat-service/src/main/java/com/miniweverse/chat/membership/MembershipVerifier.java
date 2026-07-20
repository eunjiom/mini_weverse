package com.miniweverse.chat.membership;

import java.time.LocalDateTime;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * ChatChannelInterceptor(SimpMessagingTemplate을 만드는 WebSocket 브로커 설정 자체가 의존하는
 * 빈)가 이 클래스를 참조하므로, SimpMessagingTemplate을 즉시(eager) 주입하면 순환 참조가 생긴다.
 * {@code @Lazy}로 실제 사용 시점까지 해석을 미뤄 순환을 끊는다.
 */
@Component
public class MembershipVerifier {

    private final MembershipClient membershipClient;
    private final MembershipCache membershipCache;
    private final MembershipPeriodRepository membershipPeriodRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public MembershipVerifier(
            MembershipClient membershipClient,
            MembershipCache membershipCache,
            MembershipPeriodRepository membershipPeriodRepository,
            @Lazy SimpMessagingTemplate messagingTemplate
    ) {
        this.membershipClient = membershipClient;
        this.membershipCache = membershipCache;
        this.membershipPeriodRepository = membershipPeriodRepository;
        this.messagingTemplate = messagingTemplate;
    }

    public boolean isActiveMember(Long fanUserId, Long artistId) {
        Boolean cached = membershipCache.getIfPresent(fanUserId, artistId);
        if (cached != null) {
            return cached;
        }
        boolean active = membershipClient.isActive(fanUserId, artistId);
        membershipCache.put(fanUserId, artistId, active);
        return active;
    }

    /**
     * community-service가 구독 성공 직후 호출 — 캐시된 false를 자정까지 기다리지 않고 즉시 정정한다.
     * newPeriodStartedAt이 있으면(그냥 연장이 아니라 새 구독 기간이 열린 경우) 기간 이력도 새로 연다.
     * 이미 열린 기간이 있으면 새로 열지 않는다 — 아웃박스가 재시도해서 같은 활성화 이벤트가 두 번
     * 도착해도(응답 유실 후 재전송 등) 기간이 중복으로 열리지 않게 한다(중복되면 markExpired의
     * findOpenPeriod가 단건을 기대하는 쿼리라 나중에 터진다).
     */
    public void markActive(Long fanUserId, Long artistId, LocalDateTime newPeriodStartedAt) {
        membershipCache.put(fanUserId, artistId, true);
        if (newPeriodStartedAt != null && membershipPeriodRepository.findOpenPeriod(fanUserId, artistId).isEmpty()) {
            membershipPeriodRepository.save(MembershipPeriod.start(fanUserId, artistId, newPeriodStartedAt));
        }
    }

    /**
     * community-service의 자정 배치가 멤버십을 만료시킨 직후 호출 — 캐시된 true를 즉시 정정하고,
     * 열려있던 기간 이력을 닫고, 그 팬이 지금 접속 중이면(화면을 이미 켜놓고 있었다면) 만료 알림을
     * 한 번 보낸다. 접속 중이 아니면 그냥 버려진다(별도 저장/재전송 안 함) — 다음에 REST로 이력을
     * 조회하면 403으로 걸러진다.
     */
    public void markExpired(Long fanUserId, Long artistId, LocalDateTime periodEndedAt) {
        membershipCache.put(fanUserId, artistId, false);
        if (periodEndedAt != null) {
            membershipPeriodRepository.findOpenPeriod(fanUserId, artistId)
                    .ifPresent(period -> {
                        period.close(periodEndedAt);
                        membershipPeriodRepository.save(period);
                    });
        }
        messagingTemplate.convertAndSendToUser(
                String.valueOf(fanUserId),
                "/queue/rooms/" + artistId + "/notice",
                MembershipExpiredNotice.of(artistId)
        );
    }
}
