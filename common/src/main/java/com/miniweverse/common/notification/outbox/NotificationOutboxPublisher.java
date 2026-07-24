package com.miniweverse.common.notification.outbox;

import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * NotificationOutboxEvent를 주기적으로 폴링해 NotificationOutboxEventProcessor에 실제 Kafka
 * 발행을 위임한다. MembershipOutboxPublisher와 동일한 패턴.
 */
@Component
public class NotificationOutboxPublisher {

    private static final int BATCH_SIZE = 100;

    private final NotificationOutboxEventRepository outboxEventRepository;
    private final NotificationOutboxEventProcessor eventProcessor;

    public NotificationOutboxPublisher(
            NotificationOutboxEventRepository outboxEventRepository,
            NotificationOutboxEventProcessor eventProcessor
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.eventProcessor = eventProcessor;
    }

    @Scheduled(fixedDelay = 5000)
    public void publishPendingEvents() {
        List<NotificationOutboxEvent> events = outboxEventRepository
                .findByStatusOrderByIdAsc(NotificationOutboxStatus.PENDING, PageRequest.of(0, BATCH_SIZE));
        events.forEach(eventProcessor::process);
    }
}
