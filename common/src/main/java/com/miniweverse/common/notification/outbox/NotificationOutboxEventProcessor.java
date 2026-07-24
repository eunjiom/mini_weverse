package com.miniweverse.common.notification.outbox;

import com.miniweverse.common.notification.NotificationEvent;
import com.miniweverse.common.notification.NotificationTopics;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.KafkaException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * NotificationOutboxEvent 하나를 실제로 Kafka에 발행하고 결과를 커밋한다.
 * MembershipOutboxEventProcessor와 동일한 이유로 별도 빈으로 분리한다 — NotificationOutboxPublisher의
 * 스케줄러 메서드 안에서 같은 빈의 메서드를 this::로 직접 호출하면 프록시를 거치지 않아
 * @Transactional이 무시되는 자기호출(self-invocation) 문제가 생긴다.
 */
@Component
public class NotificationOutboxEventProcessor {

    private static final int MAX_ATTEMPTS = 5;

    private final NotificationOutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, NotificationEvent> kafkaTemplate;

    public NotificationOutboxEventProcessor(
            NotificationOutboxEventRepository outboxEventRepository,
            KafkaTemplate<String, NotificationEvent> kafkaTemplate
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Transactional
    public void process(NotificationOutboxEvent event) {
        try {
            NotificationEvent payload = new NotificationEvent(
                    event.getEventId(),
                    event.getType(),
                    event.getTargetUserId(),
                    event.getTitle(),
                    event.getMessage(),
                    event.getCreatedAt()
            );
            // 같은 유저의 알림이 순서 뒤바뀌지 않도록 targetUserId를 파티션 키로 써서, 같은 유저의
            // 이벤트는 항상 같은 파티션(따라서 같은 컨슈머 스레드)으로 간다.
            kafkaTemplate.send(NotificationTopics.NOTIFICATION_EVENTS, String.valueOf(event.getTargetUserId()), payload)
                    .get();
            event.markSent();
        } catch (KafkaException | InterruptedException | java.util.concurrent.ExecutionException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            event.recordFailure(MAX_ATTEMPTS);
        }
        outboxEventRepository.save(event);
    }
}
