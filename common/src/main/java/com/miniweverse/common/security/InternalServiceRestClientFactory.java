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

    public static RestClient create(String baseUrl, String internalServiceSecret) {
        Assert.hasText(internalServiceSecret, "internal.service-secret must not be blank");
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader(InternalServiceAuthFilter.SECRET_HEADER_NAME, internalServiceSecret)
                .build();
    }
}
