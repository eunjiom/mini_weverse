package com.miniweverse.auth.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

/**
 * 관리자 세션을 Redis에 저장한다. 여러 인스턴스(ASG) 환경에서도 세션을 공유할 수 있게 한다.
 */
@Configuration
@EnableRedisHttpSession
public class SessionConfig {
}
