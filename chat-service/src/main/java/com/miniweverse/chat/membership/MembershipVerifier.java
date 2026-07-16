package com.miniweverse.chat.membership;

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
    private final SimpMessagingTemplate messagingTemplate;

    public MembershipVerifier(
            MembershipClient membershipClient,
            MembershipCache membershipCache,
            @Lazy SimpMessagingTemplate messagingTemplate
    ) {
        this.membershipClient = membershipClient;
        this.membershipCache = membershipCache;
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

    /** community-service가 구독 성공 직후 호출 — 캐시된 false를 자정까지 기다리지 않고 즉시 정정한다. */
    public void markActive(Long fanUserId, Long artistId) {
        membershipCache.put(fanUserId, artistId, true);
    }

    /**
     * community-service의 자정 배치가 멤버십을 만료시킨 직후 호출 — 캐시된 true를 즉시 정정하고,
     * 그 팬이 지금 접속 중이면(화면을 이미 켜놓고 있었다면) 만료 알림을 한 번 보낸다. 접속 중이
     * 아니면 그냥 버려진다(별도 저장/재전송 안 함) — 다음에 REST로 이력을 조회하면 403으로 걸러진다.
     */
    public void markExpired(Long fanUserId, Long artistId) {
        membershipCache.put(fanUserId, artistId, false);
        messagingTemplate.convertAndSendToUser(
                String.valueOf(fanUserId),
                "/queue/rooms/" + artistId + "/notice",
                MembershipExpiredNotice.of(artistId)
        );
    }
}
