package com.miniweverse.auth;

import java.time.Duration;
import java.util.Optional;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * RT를 Redis 해시(refresh:{userId}, field=token)로 저장/조회/삭제한다.
 * Rotation, 일치 검증 등은 이 저장소를 사용하는 재발급 API 쪽에서 다룬다.
 */
@Repository
public class RefreshTokenRepository {

    private static final String KEY_PREFIX = "refresh:";
    private static final String FIELD_TOKEN = "token";

    private final StringRedisTemplate redisTemplate;
    private final HashOperations<String, String, String> hashOperations;
    private final JwtProperties jwtProperties;

    public RefreshTokenRepository(StringRedisTemplate redisTemplate, JwtProperties jwtProperties) {
        this.redisTemplate = redisTemplate;
        this.hashOperations = redisTemplate.opsForHash();
        this.jwtProperties = jwtProperties;
    }

    public void save(Long userId, String refreshToken) {
        String key = key(userId);
        hashOperations.put(key, FIELD_TOKEN, refreshToken);
        redisTemplate.expire(key, Duration.ofMillis(jwtProperties.refreshTokenValidity()));
    }

    public Optional<String> findRefreshToken(Long userId) {
        return Optional.ofNullable(hashOperations.get(key(userId), FIELD_TOKEN));
    }

    public void delete(Long userId) {
        redisTemplate.delete(key(userId));
    }

    private String key(Long userId) {
        return KEY_PREFIX + userId;
    }
}
