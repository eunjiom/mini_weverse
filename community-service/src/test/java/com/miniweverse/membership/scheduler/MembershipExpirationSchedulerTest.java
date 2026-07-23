package com.miniweverse.membership.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import com.miniweverse.membership.service.MembershipService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

/**
 * 실제 DB 동시성 경합(낙관적 락 충돌)은 타이밍에 의존해 결정적으로 재현하기 어려우므로,
 * MembershipService를 목으로 대체해 "한 건이 예외를 던져도 배치 전체가 멈추지 않는다"는
 * 스케줄러의 제어 흐름만 검증한다.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class MembershipExpirationSchedulerTest {

    @Mock
    private MembershipService membershipService;

    private MembershipExpirationScheduler scheduler;

    @Test
    void 낙관적_락_충돌로_한_건이_실패해도_나머지_건은_계속_처리된다() {
        scheduler = new MembershipExpirationScheduler(membershipService);
        given(membershipService.findOverdueMembershipIds(any())).willReturn(List.of(1L, 2L, 3L));
        willThrow(new ObjectOptimisticLockingFailureException("Membership", 2L))
                .given(membershipService).expireOne(eq(2L), any(LocalDateTime.class));

        scheduler.expireOverdueMemberships();

        verify(membershipService).expireOne(eq(1L), any(LocalDateTime.class));
        verify(membershipService).expireOne(eq(2L), any(LocalDateTime.class));
        verify(membershipService).expireOne(eq(3L), any(LocalDateTime.class));
    }

    @Test
    void 예상치_못한_일반_예외가_나도_나머지_건은_계속_처리된다() {
        scheduler = new MembershipExpirationScheduler(membershipService);
        given(membershipService.findOverdueMembershipIds(any())).willReturn(List.of(10L, 20L));
        willThrow(new IllegalStateException("unexpected"))
                .given(membershipService).expireOne(eq(10L), any(LocalDateTime.class));

        scheduler.expireOverdueMemberships();

        verify(membershipService).expireOne(eq(10L), any(LocalDateTime.class));
        verify(membershipService).expireOne(eq(20L), any(LocalDateTime.class));
    }
}
