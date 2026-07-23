package com.miniweverse.membership.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

@Tag("unit")
class MembershipOutboxEventTest {

    private static final int MAX_ATTEMPTS = 5;

    @Test
    void 생성_직후에는_PENDING_상태이고_attemptCount는_0이다() {
        MembershipOutboxEvent event = MembershipOutboxEvent.activated(1L, 2L, LocalDateTime.now());

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(event.getAttemptCount()).isZero();
    }

    @Test
    void markSent을_호출하면_SENT_상태로_바뀐다() {
        MembershipOutboxEvent event = MembershipOutboxEvent.expired(1L, 2L, LocalDateTime.now());

        event.markSent();

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.SENT);
    }

    @Test
    void recordFailure를_maxAttempts_미만으로_호출하면_PENDING을_유지한다() {
        MembershipOutboxEvent event = MembershipOutboxEvent.activated(1L, 2L, LocalDateTime.now());

        for (int i = 0; i < MAX_ATTEMPTS - 1; i++) {
            event.recordFailure(MAX_ATTEMPTS);
        }

        assertThat(event.getAttemptCount()).isEqualTo(MAX_ATTEMPTS - 1);
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
    }

    @Test
    void recordFailure가_maxAttempts에_도달하면_DEAD로_전환된다() {
        MembershipOutboxEvent event = MembershipOutboxEvent.activated(1L, 2L, LocalDateTime.now());

        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            event.recordFailure(MAX_ATTEMPTS);
        }

        assertThat(event.getAttemptCount()).isEqualTo(MAX_ATTEMPTS);
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.DEAD);
    }
}
