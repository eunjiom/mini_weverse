package com.miniweverse.membership.client;

import com.miniweverse.common.security.InternalServiceRestClientFactory;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
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

    private final RestClient restClient;

    public ChatServiceMembershipNotifier(
            RestClient.Builder restClientBuilder,
            @Value("${chat-service.base-url}") String baseUrl,
            @Value("${internal.service-secret}") String internalServiceSecret
    ) {
        this.restClient = InternalServiceRestClientFactory.create(restClientBuilder, baseUrl, internalServiceSecret);
    }

    /** @param newPeriodStartedAt 이번 활성화로 새 구독 기간이 열렸으면 그 시작 시각, 그냥 연장이면 null */
    public void notifyActivated(Long fanUserId, Long artistId, LocalDateTime newPeriodStartedAt) {
        restClient.post()
                .uri("/internal/memberships/active")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new MembershipEventPayload(fanUserId, artistId, newPeriodStartedAt))
                .retrieve()
                .toBodilessEntity();
    }

    public void notifyExpired(Long fanUserId, Long artistId, LocalDateTime periodEndedAt) {
        restClient.post()
                .uri("/internal/memberships/expired")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new MembershipEventPayload(fanUserId, artistId, periodEndedAt))
                .retrieve()
                .toBodilessEntity();
    }

    private record MembershipEventPayload(Long fanUserId, Long artistId, LocalDateTime periodBoundaryAt) {
    }
}
