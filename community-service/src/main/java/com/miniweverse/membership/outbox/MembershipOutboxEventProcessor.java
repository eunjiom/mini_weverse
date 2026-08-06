package com.miniweverse.membership.outbox;

import com.miniweverse.common.membership.MembershipSyncEvent;
import com.miniweverse.common.membership.MembershipSyncEventType;
import com.miniweverse.common.membership.MembershipSyncTopics;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * MembershipOutboxEvent 하나를 실제로 Kafka에 발행하고 결과를 커밋한다. MembershipOutboxPublisher의
 * 스케줄러 메서드 안에서 같은 빈의 메서드를 this::로 직접 호출하면 프록시를 거치지 않아
 * @Transactional이 무시되는 자기호출(self-invocation) 문제가 생기므로, 별도 빈으로 분리해
 * 진짜 프록시 경유 호출이 되도록 한다.
 */
@Component
public class MembershipOutboxEventProcessor {

    private static final int MAX_ATTEMPTS = 5;
    // Kafka 응답을 무기한 기다리면 이 스케줄러 스레드가 멈춰서 폴링 자체가 안 돈다 — 폴링
    // 주기(5초)보다 짧게 상한을 둬서, 응답이 없어도 실패로 기록하고 다음 폴링에 재시도되게 한다.
    private static final long KAFKA_SEND_TIMEOUT_SECONDS = 3;

    private final MembershipOutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public MembershipOutboxEventProcessor(
            MembershipOutboxEventRepository outboxEventRepository,
            KafkaTemplate<String, Object> kafkaTemplate
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Transactional
    public void process(MembershipOutboxEvent event) {
        try {
            MembershipSyncEventType eventType = switch (event.getEventType()) {
                case MEMBERSHIP_ACTIVATED -> MembershipSyncEventType.ACTIVATED;
                case MEMBERSHIP_EXPIRED -> MembershipSyncEventType.EXPIRED;
            };
            MembershipSyncEvent payload = new MembershipSyncEvent(
                    eventType, event.getFanUserId(), event.getArtistId(), event.getPeriodBoundaryAt());
            // 같은 팬의 이벤트가 순서 뒤바뀌지 않도록 fanUserId를 파티션 키로 써서, 같은 팬의
            // 이벤트는 항상 같은 파티션(따라서 같은 컨슈머 스레드)으로 간다.
            kafkaTemplate.send(MembershipSyncTopics.MEMBERSHIP_SYNC_EVENTS, String.valueOf(event.getFanUserId()), payload)
                    .get(KAFKA_SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            event.markSent();
        } catch (KafkaException | InterruptedException | ExecutionException | TimeoutException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            event.recordFailure(MAX_ATTEMPTS);
        }
        // event가 findByStatusOrderByIdAsc를 호출한 이전 트랜잭션에서 넘어온 detached 상태일 수
        // 있어서, 이 트랜잭션의 영속성 컨텍스트에 확실히 반영되도록 명시적으로 저장한다.
        outboxEventRepository.save(event);
    }
}
