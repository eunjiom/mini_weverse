package com.miniweverse.membership.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 구독 성공 직후 chat-service에 "이 팬-아티스트 조합 활성화됨"을 알려 캐시를 즉시 정정시킨다
 * (MembershipCache가 false도 자정까지 캐싱하기 때문에, 방금 구독한 팬이 자정까지 차단되지
 * 않으려면 이 푸시가 필요하다). 게이트웨이를 거치지 않고 내부망에서 chat-service를 직접 호출한다.
 *
 * 이 알림은 순수 캐시 최적화 목적이라, 실패해도 구독 자체를 실패시키지 않는다 — chat-service는
 * 캐시가 없으면 필요할 때 직접 조회하는 경로(MembershipVerifier)를 갖고 있어 정합성엔 문제없다.
 */
@Component
public class ChatServiceMembershipNotifier {

    private final RestClient restClient;

    public ChatServiceMembershipNotifier(@Value("${chat-service.base-url}") String baseUrl) {
        this.restClient = RestClient.create(baseUrl);
    }

    public void notifyActivated(Long fanUserId, Long artistId) {
        try {
            restClient.post()
                    .uri("/internal/memberships/active")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ActivatedPayload(fanUserId, artistId))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            // chat-service가 잠시 내려가 있어도 구독 자체는 성공 처리한다 — 캐시는 나중에 직접 조회로 채워짐.
        }
    }

    private record ActivatedPayload(Long fanUserId, Long artistId) {
    }
}
