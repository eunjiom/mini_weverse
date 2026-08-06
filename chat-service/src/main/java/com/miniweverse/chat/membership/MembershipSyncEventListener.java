package com.miniweverse.chat.membership;

import com.miniweverse.common.membership.MembershipSyncEvent;
import com.miniweverse.common.membership.MembershipSyncTopics;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * community-service가 구독/만료 시점에 발행하는 멤버십 상태 변경 이벤트를 소비해 캐시를 즉시
 * 정정시킨다(MembershipCache가 false도 자정까지 캐싱하기 때문에 이 반영이 필요하다).
 * 예전엔 community-service가 REST로 직접 호출했으나, chat-service가 오래 다운돼 있으면 재시도
 * 한도(5회)를 넘겨 이벤트가 DEAD 처리되고 방치되는 문제가 있어 Kafka로 전환했다 — 컨슈머가
 * 복구되면 밀린 이벤트를 그대로 이어받아 처리한다.
 */
@Component
public class MembershipSyncEventListener {

    private final MembershipVerifier membershipVerifier;

    public MembershipSyncEventListener(MembershipVerifier membershipVerifier) {
        this.membershipVerifier = membershipVerifier;
    }

    @KafkaListener(topics = MembershipSyncTopics.MEMBERSHIP_SYNC_EVENTS)
    public void onMembershipSyncEvent(MembershipSyncEvent event) {
        switch (event.eventType()) {
            case ACTIVATED -> membershipVerifier.markActive(event.fanUserId(), event.artistId(), event.periodBoundaryAt());
            case EXPIRED -> membershipVerifier.markExpired(event.fanUserId(), event.artistId(), event.periodBoundaryAt());
        }
    }
}
