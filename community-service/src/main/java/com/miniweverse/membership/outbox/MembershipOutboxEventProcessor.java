package com.miniweverse.membership.outbox;

import com.miniweverse.membership.client.ChatServiceMembershipNotifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

/**
 * MembershipOutboxEvent 하나를 실제로 전송하고 결과를 커밋한다. MembershipOutboxPublisher의
 * 스케줄러 메서드 안에서 같은 빈의 메서드를 this::로 직접 호출하면 프록시를 거치지 않아
 * @Transactional이 무시되는 자기호출(self-invocation) 문제가 생기므로, 별도 빈으로 분리해
 * 진짜 프록시 경유 호출이 되도록 한다.
 */
@Component
public class MembershipOutboxEventProcessor {

    private static final int MAX_ATTEMPTS = 5;

    private final MembershipOutboxEventRepository outboxEventRepository;
    private final ChatServiceMembershipNotifier chatServiceMembershipNotifier;

    public MembershipOutboxEventProcessor(
            MembershipOutboxEventRepository outboxEventRepository,
            ChatServiceMembershipNotifier chatServiceMembershipNotifier
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.chatServiceMembershipNotifier = chatServiceMembershipNotifier;
    }

    @Transactional
    public void process(MembershipOutboxEvent event) {
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
        // event가 findByStatusOrderByIdAsc를 호출한 이전 트랜잭션에서 넘어온 detached 상태일 수
        // 있어서, 이 트랜잭션의 영속성 컨텍스트에 확실히 반영되도록 명시적으로 저장한다.
        outboxEventRepository.save(event);
    }
}
