package com.miniweverse.chat.websocket;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 위버스 DM 방식 — 팬은 아티스트 한 명당 하루 5개까지만 메시지(답장)를 보낼 수 있고,
 * 자정에 초기화된다. 아티스트의 방송에는 이 제한이 없다.
 *
 * chat-service 인스턴스가 여러 개로 늘어나도 같은 카운터를 공유해야 해서 Caffeine(로컬) 대신
 * Redis의 원자적 INCR을 쓴다. 자정 만료는 카운터가 이번 요청으로 처음 생긴 경우(count == 1)에만
 * 설정한다 — 그 이후 요청들이 매번 TTL을 다시 세팅하면 "마지막 메시지 시각 + 하루"로 만료 시점이
 * 계속 밀리게 되어 자정 초기화가 아니게 된다.
 */
@Component
public class FanMessageDailyQuota {

    private static final String KEY_PREFIX = "chat:quota:";
    private static final int MAX_MESSAGES_PER_DAY = 5;
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final StringRedisTemplate redisTemplate;

    public FanMessageDailyQuota(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean tryAcquire(Long fanUserId, Long artistId) {
        String key = key(fanUserId, artistId);
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expireAt(key, nextMidnight());
        }
        return count != null && count <= MAX_MESSAGES_PER_DAY;
    }

    private Instant nextMidnight() {
        return LocalDate.now(ZONE).plusDays(1).atStartOfDay(ZONE).toInstant();
    }

    private String key(Long fanUserId, Long artistId) {
        return KEY_PREFIX + fanUserId + ":" + artistId;
    }
}
