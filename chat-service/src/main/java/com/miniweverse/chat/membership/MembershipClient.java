package com.miniweverse.chat.membership;

import com.miniweverse.common.security.InternalServiceRestClientFactory;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(MembershipClient.class);

    private final RestClient restClient;

    public MembershipClient(
            @Value("${community-service.base-url}") String baseUrl,
            @Value("${internal.service-secret}") String internalServiceSecret
    ) {
        this.restClient = InternalServiceRestClientFactory.create(baseUrl, internalServiceSecret);
    }

    /**
     * community-service 호출 자체가 실패하면(네트워크 오류, 타임아웃 등) {@code Optional.empty()}를
     * 반환한다 — "진짜 비활성"과 "확인 실패"를 구분해야, 호출부(MembershipVerifier)가 일시적 장애를
     * 자정까지 유지되는 캐시에 "비활성"으로 잘못 굳혀버리는 걸 피할 수 있다.
     */
    public Optional<Boolean> isActive(Long fanUserId, Long artistId) {
        try {
            MembershipActiveResponse response = restClient.get()
                    .uri("/internal/memberships/active?subscriberId={subscriberId}&artistId={artistId}", fanUserId, artistId)
                    .retrieve()
                    .body(MembershipActiveResponse.class);
            return Optional.of(response != null && response.active());
        } catch (RestClientException e) {
            log.warn("community-service 멤버십 확인 실패, fanUserId={}, artistId={}", fanUserId, artistId, e);
            return Optional.empty();
        }
    }

    private record MembershipActiveResponse(boolean active) {
    }
}
