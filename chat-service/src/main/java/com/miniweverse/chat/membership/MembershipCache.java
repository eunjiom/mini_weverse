package com.miniweverse.chat.membership;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
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

    private static final Logger log = LoggerFactory.getLogger(MembershipCache.class);

    private static final String KEY_PREFIX = "chat:membership:";
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final StringRedisTemplate redisTemplate;

    public MembershipCache(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Redis 자체가 응답하지 않는 경우도 "캐시 미스"와 똑같이 null을 반환한다 — 호출부
     * (MembershipVerifier)가 그대로 membershipClient.isActive() 원격 확인으로 폴백한다.
     */
    public Boolean getIfPresent(Long fanUserId, Long artistId) {
        try {
            String value = redisTemplate.opsForValue().get(key(fanUserId, artistId));
            return value == null ? null : Boolean.valueOf(value);
        } catch (DataAccessException e) {
            log.warn("Redis 조회 실패, 캐시 미스로 간주, fanUserId={}, artistId={}", fanUserId, artistId, e);
            return null;
        }
    }

    /**
     * set과 만료 설정을 한 번의 원자적 명령으로 묶어서, 둘 사이에 죽어도 TTL 없는 키가 남지 않게 한다.
     * 이 저장이 실패해도 이미 확보한 조회 결과(active)까지 무효로 만들 이유는 없어서, 예외를 밖으로
     * 던지지 않고 로그만 남긴다 — 다음 조회 때 다시 캐시 미스로 폴백하면 그만이다.
     */
    public void put(Long fanUserId, Long artistId, boolean active) {
        try {
            redisTemplate.opsForValue().set(key(fanUserId, artistId), String.valueOf(active), untilNextMidnight());
        } catch (DataAccessException e) {
            log.warn("Redis 저장 실패, fanUserId={}, artistId={}, active={}", fanUserId, artistId, active, e);
        }
    }

    private Duration untilNextMidnight() {
        LocalDateTime now = LocalDateTime.now(ZONE);
        LocalDateTime nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay();
        return Duration.between(now, nextMidnight);
    }

    private String key(Long fanUserId, Long artistId) {
        return KEY_PREFIX + fanUserId + ":" + artistId;
    }
}
