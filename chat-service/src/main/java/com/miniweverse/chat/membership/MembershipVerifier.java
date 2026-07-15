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
        if (active) {
            membershipCache.putActive(fanUserId, artistId);
        }
        return active;
    }
}
