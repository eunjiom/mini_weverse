package com.miniweverse.common.notification.outbox;

import com.miniweverse.common.notification.NotificationEvent;
import com.miniweverse.common.notification.NotificationTopics;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
    // Kafka 응답을 무기한 기다리면 이 스케줄러 스레드가 멈춰서 폴링 자체가 안 돈다 — 폴링
    // 주기(5초)보다 짧게 상한을 둬서, 응답이 없어도 실패로 기록하고 다음 폴링에 재시도되게 한다.
    private static final long KAFKA_SEND_TIMEOUT_SECONDS = 3;

    private final NotificationOutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    // Spring Boot가 자동 구성하는 기본 KafkaTemplate 빈은 KafkaTemplate<Object, Object> 타입이라,
    // KafkaTemplate<String, NotificationEvent>처럼 구체 타입으로 주입받으려 하면 제네릭이 안 맞아
    // "No qualifying bean" 오류가 난다(실행 중 실제로 발생 확인). 페이로드 타입은 런타임에는 어차피
    // 지워지므로(erasure), 기본 빈과 그대로 맞는 KafkaTemplate<String, Object>로 주입받는다.
    public NotificationOutboxEventProcessor(
            NotificationOutboxEventRepository outboxEventRepository,
            KafkaTemplate<String, Object> kafkaTemplate
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
                    .get(KAFKA_SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            event.markSent();
        } catch (KafkaException | InterruptedException | ExecutionException | TimeoutException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            event.recordFailure(MAX_ATTEMPTS);
        }
        outboxEventRepository.save(event);
    }
}
