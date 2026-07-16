package com.miniweverse.membership.outbox;

import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * MembershipOutboxEvent를 주기적으로 폴링해 MembershipOutboxEventProcessor에 실제 전송을
 * 위임한다. 전송에 실패하면 attemptCount만 늘려두고 다음 폴링에서 같은 이벤트를 다시 시도하며,
 * MAX_ATTEMPTS를 넘기면 DEAD로 표시하고 더 이상 자동 재시도하지 않는다(운영 확인 대상).
 */
@Component
public class MembershipOutboxPublisher {

    private static final int BATCH_SIZE = 50;

    private final MembershipOutboxEventRepository outboxEventRepository;
    private final MembershipOutboxEventProcessor eventProcessor;

    public MembershipOutboxPublisher(
            MembershipOutboxEventRepository outboxEventRepository,
            MembershipOutboxEventProcessor eventProcessor
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.eventProcessor = eventProcessor;
    }

    @Scheduled(fixedDelay = 5000)
    public void publishPendingEvents() {
        List<MembershipOutboxEvent> events = outboxEventRepository
                .findByStatusOrderByIdAsc(OutboxEventStatus.PENDING, PageRequest.of(0, BATCH_SIZE));
        events.forEach(eventProcessor::process);
    }
}
