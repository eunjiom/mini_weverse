package com.miniweverse.support;

import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * community-service의 RedisTestSupport와 동일한 싱글턴 컨테이너 패턴(모듈이 달라 test 소스셋을
 * 공유할 수 없어 그대로 복제). Spring 컨텍스트 없이 StringRedisTemplate만 필요한 테스트에서 쓴다.
 */
@Testcontainers
public abstract class RedisTestSupport {

    private static final int REDIS_PORT = 6379;

    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(REDIS_PORT);

    static {
        REDIS.start();
    }

    protected StringRedisTemplate newRedisTemplate() {
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(
                REDIS.getHost(), REDIS.getMappedPort(REDIS_PORT)
        );
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(configuration);
        connectionFactory.afterPropertiesSet();
        StringRedisTemplate redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        return redisTemplate;
    }
}
