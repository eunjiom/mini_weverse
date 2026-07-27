package com.miniweverse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

// chat-service는 JPA(PostgreSQL)와 Redis를 같이 쓰다 보니, 스프링이 리포지토리 인터페이스마다
// "이게 JPA용인지 Redis용인지" 자동으로 판단하는 과정에서 공통 모듈의 NotificationOutboxEventRepository를
// 모호하다고 보고 잘못 연결하는 문제가 있었다("Could not safely identify store assignment" 경고,
// 실제로 findByStatusOrderByIdAsc가 항상 빈 결과만 반환해 알림 아웃박스가 영원히 PENDING에 멈춤).
// @EnableJpaRepositories만으로는 Redis 리포지토리 자동설정이 여전히 같이 활성화돼있어 모호함이
// 안 없어졌음 - chat-service는 Redis를 RedisTemplate으로 직접 쓰기만 하고 Redis용 리포지토리
// 인터페이스(@RedisHash 등)는 하나도 안 쓰므로, 그 자동설정 자체를 꺼서 모호함의 원천을 없앤다.
@EnableJpaRepositories(basePackages = "com.miniweverse")
@EnableJpaAuditing
@EnableScheduling
@ConfigurationPropertiesScan
@SpringBootApplication(exclude = DataRedisRepositoriesAutoConfiguration.class)
public class MiniWeverseChatApplication {

    public static void main(String[] args) {
        SpringApplication.run(MiniWeverseChatApplication.class, args);
    }

}
