package com.miniweverse.membership.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import com.miniweverse.membership.client.ChatServiceMembershipNotifier;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class MembershipOutboxEventProcessorTest {

    @Mock
    private MembershipOutboxEventRepository outboxEventRepository;
    @Mock
    private ChatServiceMembershipNotifier chatServiceMembershipNotifier;

    private MembershipOutboxEventProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new MembershipOutboxEventProcessor(outboxEventRepository, chatServiceMembershipNotifier);
    }

    @Test
    void 활성화_이벤트_전송에_성공하면_notifyActivated를_호출하고_SENT로_전환한다() {
        MembershipOutboxEvent event = MembershipOutboxEvent.activated(1L, 2L, LocalDateTime.now());

        processor.process(event);

        verify(chatServiceMembershipNotifier).notifyActivated(1L, 2L, event.getPeriodBoundaryAt());
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.SENT);
        verify(outboxEventRepository).save(event);
    }

    @Test
    void 만료_이벤트_전송에_성공하면_notifyExpired를_호출하고_SENT로_전환한다() {
        MembershipOutboxEvent event = MembershipOutboxEvent.expired(1L, 2L, LocalDateTime.now());

        processor.process(event);

        verify(chatServiceMembershipNotifier).notifyExpired(1L, 2L, event.getPeriodBoundaryAt());
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.SENT);
    }

    @Test
    void chat_service_호출이_실패하면_상태를_SENT로_바꾸지_않고_실패_횟수만_기록한다() {
        MembershipOutboxEvent event = MembershipOutboxEvent.activated(1L, 2L, LocalDateTime.now());
        willThrow(new RestClientException("connection refused"))
                .given(chatServiceMembershipNotifier).notifyActivated(1L, 2L, event.getPeriodBoundaryAt());

        processor.process(event);

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(event.getAttemptCount()).isEqualTo(1);
        verify(outboxEventRepository).save(event);
    }

    @Test
    void 실패가_반복돼_MAX_ATTEMPTS에_도달하면_DEAD로_전환된다() {
        MembershipOutboxEvent event = MembershipOutboxEvent.activated(1L, 2L, LocalDateTime.now());
        willThrow(new RestClientException("connection refused"))
                .given(chatServiceMembershipNotifier).notifyActivated(1L, 2L, event.getPeriodBoundaryAt());

        for (int i = 0; i < 5; i++) {
            processor.process(event);
        }

        assertThat(event.getAttemptCount()).isEqualTo(5);
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.DEAD);
    }
}
