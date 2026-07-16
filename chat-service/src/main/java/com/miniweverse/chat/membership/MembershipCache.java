package com.miniweverse.chat.membership;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

/**
 * 멤버십 상태가 바뀌는 경로는 딱 두 가지뿐이다:
 * 1) 자정 스케줄러(community-service) — ACTIVE→EXPIRED만 만든다
 * 2) 구독(subscribe) 성공 시 community-service가 즉시 markActive를 호출해 캐시를 바로 정정한다
 *
 * 이 두 경로가 상태 변화를 전부 커버하므로, true/false 둘 다 "다음 자정까지" 안전하게
 * 캐싱할 수 있다 — false로 캐싱된 뒤 구독이 들어와도 markActive가 즉시 true로 덮어쓴다.
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

    public void put(Long fanUserId, Long artistId, boolean active) {
        cache.put(new CacheKey(fanUserId, artistId), active);
    }

    private long untilNextMidnightNanos() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextMidnight = LocalDate.now().plusDays(1).atStartOfDay();
        return Duration.between(now, nextMidnight).toNanos();
    }

    private record CacheKey(Long fanUserId, Long artistId) {
    }
}
