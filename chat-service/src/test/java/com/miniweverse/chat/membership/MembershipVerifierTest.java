package com.miniweverse.chat.membership;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class MembershipVerifierTest {

    private static final Long FAN_USER_ID = 1L;
    private static final Long ARTIST_ID = 2L;

    @Mock
    private MembershipClient membershipClient;
    @Mock
    private MembershipCache membershipCache;
    @Mock
    private MembershipPeriodRepository membershipPeriodRepository;
    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private MembershipVerifier verifier;

    @BeforeEach
    void setUp() {
        verifier = new MembershipVerifier(membershipClient, membershipCache, membershipPeriodRepository, messagingTemplate);
    }

    @Test
    void 캐시에_값이_있으면_원격_확인_없이_캐시값을_그대로_반환한다() {
        given(membershipCache.getIfPresent(FAN_USER_ID, ARTIST_ID)).willReturn(Boolean.TRUE);

        boolean active = verifier.isActiveMember(FAN_USER_ID, ARTIST_ID);

        assertThat(active).isTrue();
        verify(membershipClient, never()).isActive(any(), any());
    }

    @Test
    void 캐시_미스면_원격으로_확인하고_결과를_캐시에_저장한다() {
        given(membershipCache.getIfPresent(FAN_USER_ID, ARTIST_ID)).willReturn(null);
        given(membershipClient.isActive(FAN_USER_ID, ARTIST_ID)).willReturn(Optional.of(true));

        boolean active = verifier.isActiveMember(FAN_USER_ID, ARTIST_ID);

        assertThat(active).isTrue();
        verify(membershipCache).put(FAN_USER_ID, ARTIST_ID, true);
    }

    @Test
    void 원격_확인_자체가_실패하면_false를_반환하고_캐시에는_남기지_않는다() {
        given(membershipCache.getIfPresent(FAN_USER_ID, ARTIST_ID)).willReturn(null);
        given(membershipClient.isActive(FAN_USER_ID, ARTIST_ID)).willReturn(Optional.empty());

        boolean active = verifier.isActiveMember(FAN_USER_ID, ARTIST_ID);

        assertThat(active).isFalse();
        verify(membershipCache, never()).put(eq(FAN_USER_ID), eq(ARTIST_ID), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void markActive는_캐시를_true로_정정하고_새_기간을_연다() {
        LocalDateTime startedAt = LocalDateTime.now();

        verifier.markActive(FAN_USER_ID, ARTIST_ID, startedAt);

        verify(membershipCache).put(FAN_USER_ID, ARTIST_ID, true);
        verify(membershipPeriodRepository).save(any(MembershipPeriod.class));
    }

    @Test
    void markActive가_유니크_제약_위반으로_실패해도_예외를_전파하지_않는다() {
        LocalDateTime startedAt = LocalDateTime.now();
        willThrow(new DataIntegrityViolationException("duplicate open period"))
                .given(membershipPeriodRepository).save(any(MembershipPeriod.class));

        verifier.markActive(FAN_USER_ID, ARTIST_ID, startedAt);

        verify(membershipCache).put(FAN_USER_ID, ARTIST_ID, true);
    }

    @Test
    void markExpired는_캐시를_false로_정정하고_열린_기간을_닫고_만료_알림을_보낸다() {
        LocalDateTime endedAt = LocalDateTime.now();
        MembershipPeriod openPeriod = MembershipPeriod.start(FAN_USER_ID, ARTIST_ID, endedAt.minusDays(10));
        given(membershipPeriodRepository.findOpenPeriod(FAN_USER_ID, ARTIST_ID)).willReturn(Optional.of(openPeriod));

        verifier.markExpired(FAN_USER_ID, ARTIST_ID, endedAt);

        verify(membershipCache).put(FAN_USER_ID, ARTIST_ID, false);
        assertThat(openPeriod.getEndedAt()).isEqualTo(endedAt);
        verify(membershipPeriodRepository).save(openPeriod);
        verify(messagingTemplate).convertAndSendToUser(
                eq(String.valueOf(FAN_USER_ID)), eq("/queue/rooms/" + ARTIST_ID + "/notice"), any(Object.class)
        );
    }
}
