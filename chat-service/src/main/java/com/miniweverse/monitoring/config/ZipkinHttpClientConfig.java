package com.miniweverse.monitoring.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import zipkin2.reporter.BytesMessageSender;
import zipkin2.reporter.urlconnection.URLConnectionSender;

/**
 * Spring Boot가 기본으로 자동 구성하는 java.net.http.HttpClient 기반 Zipkin 센더가 이 컨테이너
 * 환경에서 원인 불명의 ConnectException을 계속 던졌다(같은 컨테이너 안에서 wget으로는 같은 주소가
 * 정상 응답함 — 순수 네트워크 문제는 아님. HTTP/1.1 강제, glibc 베이스 이미지 전환도 효과 없었음).
 * 구식 HttpURLConnection 기반의 URLConnectionSender로 직접 교체해서 우회한다. 이 빈이 있으면
 * Spring Boot의 기본 java.net.http.HttpClient 센더는 @ConditionalOnMissingBean으로 자동 비활성화된다.
 */
@Configuration
public class ZipkinHttpClientConfig {

    @Bean
    public BytesMessageSender zipkinSender(@Value("${management.zipkin.tracing.endpoint}") String endpoint) {
        return URLConnectionSender.newBuilder()
                .endpoint(endpoint)
                .build();
    }
}
