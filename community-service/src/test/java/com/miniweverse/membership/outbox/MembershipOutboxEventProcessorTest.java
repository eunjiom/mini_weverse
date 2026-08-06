package com.miniweverse.membership.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class MembershipOutboxEventProcessorTest {

    @Mock
    private MembershipOutboxEventRepository outboxEventRepository;
    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private MembershipOutboxEventProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new MembershipOutboxEventProcessor(outboxEventRepository, kafkaTemplate);
    }

    @Test
    void 활성화_이벤트_전송에_성공하면_Kafka에_발행하고_SENT로_전환한다() {
        MembershipOutboxEvent event = MembershipOutboxEvent.activated(1L, 2L, LocalDateTime.now());
        given(kafkaTemplate.send(anyString(), anyString(), any()))
                .willReturn(CompletableFuture.completedFuture((SendResult<String, Object>) null));

        processor.process(event);

        verify(kafkaTemplate).send(anyString(), anyString(), any());
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.SENT);
        verify(outboxEventRepository).save(event);
    }

    @Test
    void 만료_이벤트_전송에_성공하면_Kafka에_발행하고_SENT로_전환한다() {
        MembershipOutboxEvent event = MembershipOutboxEvent.expired(1L, 2L, LocalDateTime.now());
        given(kafkaTemplate.send(anyString(), anyString(), any()))
                .willReturn(CompletableFuture.completedFuture((SendResult<String, Object>) null));

        processor.process(event);

        verify(kafkaTemplate).send(anyString(), anyString(), any());
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.SENT);
    }

    @Test
    void kafka_발행이_실패하면_상태를_SENT로_바꾸지_않고_실패_횟수만_기록한다() {
        MembershipOutboxEvent event = MembershipOutboxEvent.activated(1L, 2L, LocalDateTime.now());
        willThrow(new KafkaException("broker unavailable"))
                .given(kafkaTemplate).send(anyString(), anyString(), any());

        processor.process(event);

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(event.getAttemptCount()).isEqualTo(1);
        verify(outboxEventRepository).save(event);
    }

    @Test
    void 실패가_반복돼_MAX_ATTEMPTS에_도달하면_DEAD로_전환된다() {
        MembershipOutboxEvent event = MembershipOutboxEvent.activated(1L, 2L, LocalDateTime.now());
        willThrow(new KafkaException("broker unavailable"))
                .given(kafkaTemplate).send(anyString(), anyString(), any());

        for (int i = 0; i < 5; i++) {
            processor.process(event);
        }

        assertThat(event.getAttemptCount()).isEqualTo(5);
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.DEAD);
    }
}
