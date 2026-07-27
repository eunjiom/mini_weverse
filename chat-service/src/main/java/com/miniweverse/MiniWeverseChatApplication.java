package com.miniweverse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

// chat-service는 JPA(PostgreSQL)와 Redis를 같이 쓰다 보니, 스프링이 리포지토리 인터페이스마다
// "이게 JPA용인지 Redis용인지" 자동으로 판단하는 과정에서 공통 모듈의 NotificationOutboxEventRepository를
// 모호하다고 보고 잘못 연결하는 문제가 있었다("Could not safely identify store assignment" 경고,
// 실제로 findByStatusOrderByIdAsc가 항상 빈 결과만 반환해 알림 아웃박스가 영원히 PENDING에 멈춤).
// JPA 리포지토리 스캔 범위를 명시해서 이 모호함 자체를 없앤다.
@EnableJpaRepositories(basePackages = "com.miniweverse")
@EnableJpaAuditing
@EnableScheduling
@ConfigurationPropertiesScan
@SpringBootApplication
public class MiniWeverseChatApplication {

    public static void main(String[] args) {
        SpringApplication.run(MiniWeverseChatApplication.class, args);
    }

}
