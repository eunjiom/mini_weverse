package com.miniweverse.chat.membership;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 게이트웨이를 거치지 않고 community-service를 내부망에서 직접 호출한다
 * (InternalMembershipController 참고 — 이 호출엔 유저 JWT가 없어서 게이트웨이의 인가 규칙과 무관).
 */
@Component
public class MembershipClient {

    private final RestClient restClient;

    public MembershipClient(@Value("${community-service.base-url}") String baseUrl) {
        this.restClient = RestClient.create(baseUrl);
    }

    public boolean isActive(Long fanUserId, Long artistId) {
        MembershipActiveResponse response = restClient.get()
                .uri("/internal/memberships/active?subscriberId={subscriberId}&artistId={artistId}", fanUserId, artistId)
                .retrieve()
                .body(MembershipActiveResponse.class);
        return response != null && response.active();
    }

    private record MembershipActiveResponse(boolean active) {
    }
}
