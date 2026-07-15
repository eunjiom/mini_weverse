package com.miniweverse.chat.membership;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

/**
 * 멤버십은 자정 스케줄러(community-service)에서만 ACTIVE→EXPIRED로 바뀌므로, 활성 상태(true)는
 * 하루 안에서는 안전하게 캐싱 가능 — 그래서 "다음 자정까지" 유효.
 *
 * 반대로 비활성(false)은 하루 중 아무 때나(구독하는 순간) true로 바뀔 수 있어서, false는
 * 절대 캐싱하지 않는다 — 캐싱하면 방금 구독한 팬이 자정까지 입장을 못 하게 되는 버그가 생긴다.
 */
@Component
public class MembershipCache {

    private final Cache<CacheKey, Boolean> cache = Caffeine.newBuilder()
            .expireAfter(new Expiry<CacheKey, Boolean>() {
                @Override
                public long expireAfterCreate(CacheKey key, Boolean value, long currentTime) {
                    return untilNextMidnightNanos();
                }

                @Override
                public long expireAfterUpdate(CacheKey key, Boolean value, long currentTime, long currentDuration) {
                    return currentDuration;
                }

                @Override
                public long expireAfterRead(CacheKey key, Boolean value, long currentTime, long currentDuration) {
                    return currentDuration;
                }
            })
            .build();

    public Boolean getIfPresent(Long fanUserId, Long artistId) {
        return cache.getIfPresent(new CacheKey(fanUserId, artistId));
    }

    /** false는 절대 호출하지 않는다 — 위 클래스 설명 참고. */
    public void putActive(Long fanUserId, Long artistId) {
        cache.put(new CacheKey(fanUserId, artistId), Boolean.TRUE);
    }

    private long untilNextMidnightNanos() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextMidnight = LocalDate.now().plusDays(1).atStartOfDay();
        return Duration.between(now, nextMidnight).toNanos();
    }

    private record CacheKey(Long fanUserId, Long artistId) {
    }
}
