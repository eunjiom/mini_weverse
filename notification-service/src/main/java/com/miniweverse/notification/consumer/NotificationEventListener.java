package com.miniweverse.notification.consumer;

import com.miniweverse.common.notification.NotificationEvent;
import com.miniweverse.common.notification.NotificationTopics;
import com.miniweverse.notification.repository.NotificationRedisRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * community-service/chat-service가 발행한 알림 이벤트를 소비해 Redis에 저장한다.
 * 컨슈머 그룹(application.yml의 spring.kafka.consumer.group-id)은 인스턴스가 여러 개로
 * 늘어나도 같은 이벤트를 중복 소비하지 않도록 파티션을 나눠 갖는다.
 */
@Component
public class NotificationEventListener {

    private final NotificationRedisRepository notificationRedisRepository;

    public NotificationEventListener(NotificationRedisRepository notificationRedisRepository) {
        this.notificationRedisRepository = notificationRedisRepository;
    }

    @KafkaListener(topics = NotificationTopics.NOTIFICATION_EVENTS)
    public void onNotificationEvent(NotificationEvent event) {
        notificationRedisRepository.save(event);
    }
}
