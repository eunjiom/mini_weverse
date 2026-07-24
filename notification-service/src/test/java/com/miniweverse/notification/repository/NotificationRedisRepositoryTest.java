package com.miniweverse.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.miniweverse.common.notification.NotificationEvent;
import com.miniweverse.common.notification.NotificationType;
import com.miniweverse.notification.config.NotificationProperties;
import com.miniweverse.support.RedisTestSupport;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

@Tag("integration")
class NotificationRedisRepositoryTest extends RedisTestSupport {

    private static final Long USER_ID = 1L;

    private StringRedisTemplate redisTemplate;
    private NotificationRedisRepository repository;

    @BeforeEach
    void setUp() {
        redisTemplate = newRedisTemplate();
        repository = new NotificationRedisRepository(redisTemplate, new ObjectMapper(), new NotificationProperties(7));
        // 정적(싱글턴) 컨테이너를 여러 테스트가 공유하므로, 이전 테스트가 남긴 알림/lastReadAt이
        // 실행 순서에 따라 다음 테스트에 영향을 주지 않도록 매번 깨끗하게 지우고 시작한다.
        redisTemplate.delete("notification:" + USER_ID);
        redisTemplate.delete("notification:last-read:" + USER_ID);
    }

    @Test
    void 저장한_알림은_JSON_직렬화_왕복을_거쳐도_원래_내용_그대로_조회된다() {
        NotificationEvent event = notification(NotificationType.NEW_FOLLOWER, "새 팔로워", "테스트님이 팔로우했습니다.");

        repository.save(event);
        List<NotificationEvent> recent = repository.findRecent(USER_ID, 10);

        assertThat(recent).hasSize(1);
        assertThat(recent.get(0).type()).isEqualTo(NotificationType.NEW_FOLLOWER);
        assertThat(recent.get(0).title()).isEqualTo("새 팔로워");
        assertThat(recent.get(0).message()).isEqualTo("테스트님이 팔로우했습니다.");
        assertThat(recent.get(0).occurredAt()).isEqualTo(event.occurredAt());
    }

    @Test
    void 여러_건_저장하면_최신순으로_조회된다() {
        repository.save(notificationAt(LocalDateTime.now().minusMinutes(2), "오래된 알림"));
        repository.save(notificationAt(LocalDateTime.now().minusMinutes(1), "중간 알림"));
        repository.save(notificationAt(LocalDateTime.now(), "최신 알림"));

        List<NotificationEvent> recent = repository.findRecent(USER_ID, 10);

        assertThat(recent).extracting(NotificationEvent::message)
                .containsExactly("최신 알림", "중간 알림", "오래된 알림");
    }

    @Test
    void markAllRead_이전_알림은_읽음_이후_알림은_안읽음으로_카운트된다() {
        repository.save(notificationAt(LocalDateTime.now().minusMinutes(5), "이전 알림"));

        repository.markAllRead(USER_ID);
        repository.save(notificationAt(LocalDateTime.now(), "이후 알림"));

        assertThat(repository.countUnread(USER_ID)).isEqualTo(1L);
    }

    @Test
    void 알림이_없던_유저의_안읽음_개수는_0이다() {
        assertThat(repository.countUnread(999L)).isZero();
    }

    private NotificationEvent notification(NotificationType type, String title, String message) {
        return new NotificationEvent(UUID.randomUUID().toString(), type, USER_ID, title, message, LocalDateTime.now());
    }

    private NotificationEvent notificationAt(LocalDateTime occurredAt, String message) {
        return new NotificationEvent(UUID.randomUUID().toString(), NotificationType.NEW_COMMENT, USER_ID, "제목", message, occurredAt);
    }
}
