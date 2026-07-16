package com.miniweverse.membership.outbox;

import com.miniweverse.membership.client.ChatServiceMembershipNotifier;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

/**
 * MembershipOutboxEvent를 주기적으로 폴링해 chat-service에 실제로 전달한다. 전달에 실패하면
 * attemptCount만 늘려두고 다음 폴링에서 같은 이벤트를 다시 시도하며, MAX_ATTEMPTS를 넘기면
 * DEAD로 표시하고 더 이상 자동 재시도하지 않는다(운영 확인 대상).
 */
@Component
public class MembershipOutboxPublisher {

    private static final int MAX_ATTEMPTS = 5;
    private static final int BATCH_SIZE = 50;

    private final MembershipOutboxEventRepository outboxEventRepository;
    private final ChatServiceMembershipNotifier chatServiceMembershipNotifier;

    public MembershipOutboxPublisher(
            MembershipOutboxEventRepository outboxEventRepository,
            ChatServiceMembershipNotifier chatServiceMembershipNotifier
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.chatServiceMembershipNotifier = chatServiceMembershipNotifier;
    }

    @Scheduled(fixedDelay = 5000)
    public void publishPendingEvents() {
        List<MembershipOutboxEvent> events = outboxEventRepository
                .findByStatusOrderByIdAsc(OutboxEventStatus.PENDING, PageRequest.of(0, BATCH_SIZE));
        events.forEach(this::publish);
    }

    @Transactional
    public void publish(MembershipOutboxEvent event) {
        try {
            switch (event.getEventType()) {
                case MEMBERSHIP_ACTIVATED ->
                        chatServiceMembershipNotifier.notifyActivated(event.getFanUserId(), event.getArtistId());
                case MEMBERSHIP_EXPIRED ->
                        chatServiceMembershipNotifier.notifyExpired(event.getFanUserId(), event.getArtistId());
            }
            event.markSent();
        } catch (RestClientException e) {
            event.recordFailure(MAX_ATTEMPTS);
        }
    }
}
