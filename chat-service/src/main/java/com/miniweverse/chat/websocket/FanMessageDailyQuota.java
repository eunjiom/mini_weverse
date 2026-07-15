package com.miniweverse.chat.websocket;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/**
 * 위버스 DM 방식 — 팬은 아티스트 한 명당 하루 5개까지만 메시지(답장)를 보낼 수 있고,
 * 자정에 초기화된다. 아티스트의 방송에는 이 제한이 없다.
 *
 * MembershipCache와 동일한 "다음 자정까지" 만료 패턴을 재사용한다.
 */
@Component
public class FanMessageDailyQuota {

    private static final int MAX_MESSAGES_PER_DAY = 5;

    private final Cache<QuotaKey, AtomicInteger> counters = Caffeine.newBuilder()
            .expireAfter(new Expiry<QuotaKey, AtomicInteger>() {
                @Override
                public long expireAfterCreate(QuotaKey key, AtomicInteger value, long currentTime) {
                    return untilNextMidnightNanos();
                }

                @Override
                public long expireAfterUpdate(QuotaKey key, AtomicInteger value, long currentTime, long currentDuration) {
                    return currentDuration;
                }

                @Override
                public long expireAfterRead(QuotaKey key, AtomicInteger value, long currentTime, long currentDuration) {
                    return currentDuration;
                }
            })
            .build();

    public boolean tryAcquire(Long fanUserId, Long artistId) {
        AtomicInteger counter = counters.get(new QuotaKey(fanUserId, artistId), key -> new AtomicInteger(0));
        return counter.incrementAndGet() <= MAX_MESSAGES_PER_DAY;
    }

    private long untilNextMidnightNanos() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextMidnight = LocalDate.now().plusDays(1).atStartOfDay();
        return Duration.between(now, nextMidnight).toNanos();
    }

    private record QuotaKey(Long fanUserId, Long artistId) {
    }
}
