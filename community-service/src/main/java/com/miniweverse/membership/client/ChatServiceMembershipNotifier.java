package com.miniweverse.membership.client;

import com.miniweverse.common.security.InternalServiceAuthFilter;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.client.RestClient;

/**
 * chat-service에 멤버십 활성화/만료를 알려 캐시를 즉시 정정시킨다(MembershipCache가 false도
 * 자정까지 캐싱하기 때문에 이 푸시가 필요하다). 게이트웨이를 거치지 않고 내부망에서 chat-service를
 * 직접 호출한다(InternalServiceAuthFilter가 검증하는 공유 시크릿을 헤더로 싣는다).
 *
 * 호출자는 항상 MembershipOutboxPublisher다 — 실패 시 예외를 그대로 던져서 발행자가 재시도
 * 여부(attemptCount)를 판단하게 한다. 여기서 직접 예외를 삼키지 않는다.
 */
@Component
public class ChatServiceMembershipNotifier {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(3);

    private final RestClient restClient;
    private final String internalServiceSecret;

    public ChatServiceMembershipNotifier(
            @Value("${chat-service.base-url}") String baseUrl,
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

    public void notifyActivated(Long fanUserId, Long artistId) {
        restClient.post()
                .uri("/internal/memberships/active")
                .contentType(MediaType.APPLICATION_JSON)
                .header(InternalServiceAuthFilter.SECRET_HEADER_NAME, internalServiceSecret)
                .body(new MembershipEventPayload(fanUserId, artistId))
                .retrieve()
                .toBodilessEntity();
    }

    public void notifyExpired(Long fanUserId, Long artistId) {
        restClient.post()
                .uri("/internal/memberships/expired")
                .contentType(MediaType.APPLICATION_JSON)
                .header(InternalServiceAuthFilter.SECRET_HEADER_NAME, internalServiceSecret)
                .body(new MembershipEventPayload(fanUserId, artistId))
                .retrieve()
                .toBodilessEntity();
    }

    private record MembershipEventPayload(Long fanUserId, Long artistId) {
    }
}
