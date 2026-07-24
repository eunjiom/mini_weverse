package com.miniweverse.notification.repository;

import com.miniweverse.common.notification.NotificationEvent;
import com.miniweverse.notification.config.NotificationProperties;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

/**
 * 알림을 유저별 Redis Sorted Set(notification:{userId})에 저장한다. score는 발생 시각(epoch
 * millis) — 조회 시 최신순으로 뽑고, 쓸 때마다 TTL(기본 7일)보다 오래된 항목을 정리(ZREMRANGEBYSCORE)
 * 해서 개별 알림이 실제로 7일 뒤에는 사라지게 한다. 활동이 없는 유저의 키 자체는 key-level EXPIRE로
 * 마지막 알림 후 7일이 지나면 통째로 사라진다(DB 영구 저장 없음 — 정책상 의도된 동작).
 *
 * 읽음 여부는 알림 하나하나가 아니라 "마지막으로 확인한 시각(lastReadAt)"만 별도 키에 저장한다 —
 * 그 시각 이후 도착한 알림은 전부 안읽음으로 간주한다(unread count = lastReadAt 이후 개수).
 */
@Repository
public class NotificationRedisRepository {

    private static final String NOTIFICATION_KEY_PREFIX = "notification:";
    private static final String LAST_READ_KEY_PREFIX = "notification:last-read:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public NotificationRedisRepository(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            NotificationProperties notificationProperties
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.ttl = Duration.ofDays(notificationProperties.ttlDays());
    }

    public void save(NotificationEvent event) {
        String key = notificationKey(event.targetUserId());
        // occurredAt(LocalDateTime)은 다른 서비스와 마찬가지로 시스템 기본 타임존 기준 벽시계
        // 값이다. System.currentTimeMillis()(lastReadAt, TTL 계산)와 같은 기준으로 비교하려면
        // 여기서도 UTC로 임의 고정하지 않고 시스템 기본 타임존으로 epoch millis를 구해야 한다 —
        // 안 그러면 UTC와의 시차만큼 모든 알림이 항상 "미래"로 계산되어 영원히 안읽음 처리된다.
        double score = event.occurredAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        redisTemplate.opsForZSet().add(key, serialize(event), score);

        double cutoff = System.currentTimeMillis() - ttl.toMillis();
        redisTemplate.opsForZSet().removeRangeByScore(key, Double.NEGATIVE_INFINITY, cutoff);
        redisTemplate.expire(key, ttl);
    }

    /** 최신순(발생 시각 내림차순) 상위 limit개. */
    public List<NotificationEvent> findRecent(Long userId, int limit) {
        Set<String> raw = redisTemplate.opsForZSet().reverseRange(notificationKey(userId), 0, limit - 1);
        if (raw == null) {
            return List.of();
        }
        return raw.stream().map(this::deserialize).toList();
    }

    public long countUnread(Long userId) {
        Long lastReadAt = getLastReadAt(userId);
        double lowerBound = lastReadAt != null ? lastReadAt + 1 : Double.NEGATIVE_INFINITY;
        Long count = redisTemplate.opsForZSet().count(notificationKey(userId), lowerBound, Double.POSITIVE_INFINITY);
        return count != null ? count : 0L;
    }

    public Long getLastReadAt(Long userId) {
        String value = redisTemplate.opsForValue().get(lastReadKey(userId));
        return value != null ? Long.valueOf(value) : null;
    }

    public void markAllRead(Long userId) {
        String key = lastReadKey(userId);
        redisTemplate.opsForValue().set(key, String.valueOf(System.currentTimeMillis()));
        redisTemplate.expire(key, ttl);
    }

    private String serialize(NotificationEvent event) {
        return objectMapper.writeValueAsString(event);
    }

    private NotificationEvent deserialize(String json) {
        return objectMapper.readValue(json, NotificationEvent.class);
    }

    private String notificationKey(Long userId) {
        return NOTIFICATION_KEY_PREFIX + userId;
    }

    private String lastReadKey(Long userId) {
        return LAST_READ_KEY_PREFIX + userId;
    }
}
