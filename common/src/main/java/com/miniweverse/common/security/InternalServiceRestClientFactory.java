package com.miniweverse.common.security;

import java.time.Duration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.Assert;
import org.springframework.web.client.RestClient;

/**
 * 게이트웨이를 거치지 않는 서비스 간 내부 API({@link InternalServiceAuthFilter}가 검증) 호출용
 * RestClient를 만든다. 공유 시크릿을 매 요청 기본 헤더로 실어주므로, 호출부는 URI/바디만 신경 쓰면 된다.
 * 응답 지연으로 커넥션/스레드를 무한정 붙잡지 않도록 짧은 타임아웃을 기본으로 건다.
 */
public final class InternalServiceRestClientFactory {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(3);

    private InternalServiceRestClientFactory() {
    }

    /**
     * 스프링이 자동구성한 {@link RestClient.Builder}를 주입받아 사용한다 — 이 빈에는
     * Micrometer Observation(분산 트레이싱) 계측이 이미 붙어있어서, 직접 {@code RestClient.builder()}로
     * 새로 만들 때와 달리 이 호출도 Zipkin 트레이스에 잡히고 트레이스 컨텍스트가 상대 서비스로 전파된다.
     */
    public static RestClient create(RestClient.Builder builder, String baseUrl, String internalServiceSecret) {
        Assert.hasText(internalServiceSecret, "internal.service-secret must not be blank");
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        return builder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader(InternalServiceAuthFilter.SECRET_HEADER_NAME, internalServiceSecret)
                .build();
    }
}
