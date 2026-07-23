package com.miniweverse.chat.websocket;

import static org.assertj.core.api.Assertions.assertThat;

import com.miniweverse.support.RedisTestSupport;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

@Tag("integration")
class FanMessageDailyQuotaTest extends RedisTestSupport {

    private static final int MAX_MESSAGES_PER_DAY = 5;
    private static final Long FAN_USER_ID = 1L;
    private static final Long ARTIST_ID = 2L;

    private StringRedisTemplate redisTemplate;
    private FanMessageDailyQuota quota;

    @BeforeEach
    void setUp() {
        redisTemplate = newRedisTemplate();
        quota = new FanMessageDailyQuota(redisTemplate);
        // 정적(싱글턴) 컨테이너를 여러 테스트가 공유하므로, 이전 테스트가 남긴 카운터가
        // 실행 순서에 따라 다음 테스트에 영향을 주지 않도록 매번 깨끗하게 지우고 시작한다.
        redisTemplate.delete(redisTemplate.keys("chat:quota:*"));
    }

    @Test
    void 하루_5건까지는_통과하고_6번째부터는_거부한다() {
        for (int i = 0; i < MAX_MESSAGES_PER_DAY; i++) {
            assertThat(quota.tryAcquire(FAN_USER_ID, ARTIST_ID)).isTrue();
        }

        assertThat(quota.tryAcquire(FAN_USER_ID, ARTIST_ID)).isFalse();
    }

    @Test
    void 다른_아티스트에_대한_카운터는_서로_독립적이다() {
        Long otherArtistId = 3L;
        for (int i = 0; i < MAX_MESSAGES_PER_DAY; i++) {
            quota.tryAcquire(FAN_USER_ID, ARTIST_ID);
        }

        assertThat(quota.tryAcquire(FAN_USER_ID, otherArtistId)).isTrue();
    }

    @Test
    void 첫_요청_시_자정까지의_TTL이_설정된다() {
        quota.tryAcquire(FAN_USER_ID, ARTIST_ID);

        Long ttl = redisTemplate.getExpire("chat:quota:" + FAN_USER_ID + ":" + ARTIST_ID, TimeUnit.SECONDS);

        assertThat(ttl).isNotNull();
        assertThat(ttl).isPositive();
    }
}
