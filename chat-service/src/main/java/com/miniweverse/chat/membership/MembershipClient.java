package com.miniweverse.chat.membership;

import com.miniweverse.common.security.InternalServiceRestClientFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 게이트웨이를 거치지 않고 community-service를 내부망에서 직접 호출한다
 * (InternalMembershipController 참고 — 이 호출엔 유저 JWT가 없어서 게이트웨이의 인가 규칙과 무관,
 * 대신 InternalServiceAuthFilter가 검증하는 공유 시크릿을 헤더로 싣는다).
 */
@Component
public class MembershipClient {

    private final RestClient restClient;

    public MembershipClient(
            @Value("${community-service.base-url}") String baseUrl,
            @Value("${internal.service-secret}") String internalServiceSecret
    ) {
        this.restClient = InternalServiceRestClientFactory.create(baseUrl, internalServiceSecret);
    }

    /** community-service 호출 자체가 실패하면(네트워크 오류, 타임아웃 등) 비활성으로 안전하게 처리한다. */
    public boolean isActive(Long fanUserId, Long artistId) {
        try {
            MembershipActiveResponse response = restClient.get()
                    .uri("/internal/memberships/active?subscriberId={subscriberId}&artistId={artistId}", fanUserId, artistId)
                    .retrieve()
                    .body(MembershipActiveResponse.class);
            return response != null && response.active();
        } catch (RestClientException e) {
            return false;
        }
    }

    private record MembershipActiveResponse(boolean active) {
    }
}
