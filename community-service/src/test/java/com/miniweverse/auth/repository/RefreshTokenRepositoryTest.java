package com.miniweverse.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.miniweverse.auth.jwt.JwtProperties;
import com.miniweverse.support.RedisTestSupport;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

@Tag("integration")
class RefreshTokenRepositoryTest extends RedisTestSupport {

    private static final long REFRESH_TOKEN_VALIDITY = TimeUnit.DAYS.toMillis(7);

    private StringRedisTemplate redisTemplate;
    private RefreshTokenRepository refreshTokenRepository;

    @BeforeEach
    void setUp() {
        redisTemplate = newRedisTemplate();
        JwtProperties jwtProperties = new JwtProperties("unused", 1_800_000L, REFRESH_TOKEN_VALIDITY);
        refreshTokenRepository = new RefreshTokenRepository(redisTemplate, jwtProperties);
    }

    @Test
    void save한_RT는_findRefreshToken으로_그대로_조회된다() {
        refreshTokenRepository.save(1L, "refresh-token-value");

        assertThat(refreshTokenRepository.findRefreshToken(1L)).contains("refresh-token-value");
    }

    @Test
    void save한_키의_TTL은_refreshTokenValidity_7일로_설정된다() {
        refreshTokenRepository.save(1L, "refresh-token-value");

        Long ttlSeconds = redisTemplate.getExpire("refresh:1", TimeUnit.SECONDS);
        long expectedTtlSeconds = TimeUnit.MILLISECONDS.toSeconds(REFRESH_TOKEN_VALIDITY);

        assertThat(ttlSeconds).isNotNull();
        assertThat(ttlSeconds).isCloseTo(expectedTtlSeconds, within(5L));
    }

    @Test
    void delete하면_더_이상_조회되지_않는다() {
        refreshTokenRepository.save(1L, "refresh-token-value");

        refreshTokenRepository.delete(1L);

        assertThat(refreshTokenRepository.findRefreshToken(1L)).isEmpty();
    }

    @Test
    void 저장된_적_없는_유저의_RT는_비어있다() {
        assertThat(refreshTokenRepository.findRefreshToken(999L)).isEmpty();
    }
}
