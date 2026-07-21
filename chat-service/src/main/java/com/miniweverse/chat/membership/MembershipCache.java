package com.miniweverse.chat.membership;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 멤버십 상태가 바뀌는 경로는 딱 두 가지뿐이다:
 * 1) 자정 스케줄러(community-service) — ACTIVE→EXPIRED만 만든다
 * 2) 구독(subscribe) 성공 시 community-service가 즉시 markActive를 호출해 캐시를 바로 정정한다
 *
 * 이 두 경로가 상태 변화를 전부 커버하므로, true/false 둘 다 "다음 자정까지" 안전하게
 * 캐싱할 수 있다 — false로 캐싱된 뒤 구독이 들어와도 markActive가 즉시 true로 덮어쓴다.
 *
 * chat-service 인스턴스가 여러 개로 늘어나도 전부 이 값을 공유해야 해서 Caffeine(로컬) 대신
 * Redis를 쓴다. community-service와 같은 Redis 서버를 쓰지만 DB index(1)로 분리해서 키가
 * 섞이지 않는다(community-service는 DB 0).
 */
@Component
public class MembershipCache {

    private static final String KEY_PREFIX = "chat:membership:";
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final StringRedisTemplate redisTemplate;

    public MembershipCache(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Boolean getIfPresent(Long fanUserId, Long artistId) {
        String value = redisTemplate.opsForValue().get(key(fanUserId, artistId));
        return value == null ? null : Boolean.valueOf(value);
    }

    /** set과 만료 설정을 한 번의 원자적 명령으로 묶어서, 둘 사이에 죽어도 TTL 없는 키가 남지 않게 한다. */
    public void put(Long fanUserId, Long artistId, boolean active) {
        redisTemplate.opsForValue().set(key(fanUserId, artistId), String.valueOf(active), untilNextMidnight());
    }

    private Duration untilNextMidnight() {
        LocalDateTime now = LocalDateTime.now(ZONE);
        LocalDateTime nextMidnight = LocalDate.now(ZONE).plusDays(1).atStartOfDay();
        return Duration.between(now, nextMidnight);
    }

    private String key(Long fanUserId, Long artistId) {
        return KEY_PREFIX + fanUserId + ":" + artistId;
    }
}
