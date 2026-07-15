package com.miniweverse.chat.membership;

import org.springframework.stereotype.Component;

@Component
public class MembershipVerifier {

    private final MembershipClient membershipClient;
    private final MembershipCache membershipCache;

    public MembershipVerifier(MembershipClient membershipClient, MembershipCache membershipCache) {
        this.membershipClient = membershipClient;
        this.membershipCache = membershipCache;
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
}
