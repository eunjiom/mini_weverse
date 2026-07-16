package com.miniweverse.chat.membership;

import com.miniweverse.common.security.InternalServiceAuthFilter;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.client.RestClient;

/**
 * 게이트웨이를 거치지 않고 community-service를 내부망에서 직접 호출한다
 * (InternalMembershipController 참고 — 이 호출엔 유저 JWT가 없어서 게이트웨이의 인가 규칙과 무관,
 * 대신 InternalServiceAuthFilter가 검증하는 공유 시크릿을 헤더로 싣는다).
 */
@Component
public class MembershipClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(3);

    private final RestClient restClient;
    private final String internalServiceSecret;

    public MembershipClient(
            @Value("${community-service.base-url}") String baseUrl,
            @Value("${internal.service-secret}") String internalServiceSecret
    ) {
        Assert.hasText(internalServiceSecret, "internal.service-secret must not be blank");
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
        this.internalServiceSecret = internalServiceSecret;
    }

    public boolean isActive(Long fanUserId, Long artistId) {
        MembershipActiveResponse response = restClient.get()
                .uri("/internal/memberships/active?subscriberId={subscriberId}&artistId={artistId}", fanUserId, artistId)
                .header(InternalServiceAuthFilter.SECRET_HEADER_NAME, internalServiceSecret)
                .retrieve()
                .body(MembershipActiveResponse.class);
        return response != null && response.active();
    }

    private record MembershipActiveResponse(boolean active) {
    }
}
