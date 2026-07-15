package com.miniweverse.chat.websocket;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/**
 * 유저(userId)당 고정 시간창 안에서 보낼 수 있는 메시지 수를 제한한다(도배 방지).
 * 카운터는 창이 시작된 시점부터 WINDOW가 지나면 통째로 초기화되는 단순 고정 윈도우 방식 —
 * 정교한 슬라이딩 윈도우까지는 이 규모에서 필요 없다고 판단.
 */
@Component
public class MessageRateLimiter {

    private static final int MAX_MESSAGES_PER_WINDOW = 5;
    private static final Duration WINDOW = Duration.ofSeconds(10);

    private final Cache<Long, AtomicInteger> counters = Caffeine.newBuilder()
            .expireAfterWrite(WINDOW)
            .build();

    public boolean tryAcquire(Long userId) {
        AtomicInteger counter = counters.get(userId, id -> new AtomicInteger(0));
        return counter.incrementAndGet() <= MAX_MESSAGES_PER_WINDOW;
    }
}
