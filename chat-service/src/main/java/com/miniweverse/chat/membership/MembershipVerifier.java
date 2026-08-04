package com.miniweverse.chat.membership;

import com.miniweverse.chat.broadcast.ChatBroadcastPublisher;
import java.time.LocalDateTime;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
public class MembershipVerifier {

    private final MembershipClient membershipClient;
    private final MembershipCache membershipCache;
    private final MembershipPeriodRepository membershipPeriodRepository;
    private final ChatBroadcastPublisher broadcastPublisher;

    public MembershipVerifier(
            MembershipClient membershipClient,
            MembershipCache membershipCache,
            MembershipPeriodRepository membershipPeriodRepository,
            ChatBroadcastPublisher broadcastPublisher
    ) {
        this.membershipClient = membershipClient;
        this.membershipCache = membershipCache;
        this.membershipPeriodRepository = membershipPeriodRepository;
        this.broadcastPublisher = broadcastPublisher;
    }

    /**
     * community-service 호출이 실패해서 확인 자체를 못 한 경우(Optional.empty())는 그 요청 하나만
     * 비활성으로 처리하고 캐시에는 안 남긴다 — 일시적 장애를 자정까지 유지되는 캐시에 "비활성"으로
     * 굳혀버리면, 그 사이 진짜 활성 회원이 계속 차단될 수 있다.
     */
    public boolean isActiveMember(Long fanUserId, Long artistId) {
        Boolean cached = membershipCache.getIfPresent(fanUserId, artistId);
        if (cached != null) {
            return cached;
        }
        return membershipClient.isActive(fanUserId, artistId)
                .map(active -> {
                    membershipCache.put(fanUserId, artistId, active);
                    return active;
                })
                .orElse(false);
    }

    /**
     * community-service가 구독 성공 직후 호출 — 캐시된 false를 자정까지 기다리지 않고 즉시 정정한다.
     * newPeriodStartedAt이 있으면(그냥 연장이 아니라 새 구독 기간이 열린 경우) 기간 이력도 새로 연다.
     * "확인 후 저장"이 아니라 바로 저장을 시도하고 실패를 잡는 방식이다 — 확인-후-저장은 두 요청이
     * 거의 동시에 들어오면(아웃박스 재시도 타이밍이 겹치는 경우 등) 확인 단계를 둘 다 통과해버릴 수
     * 있는 경합이 있다. DB의 부분 유니크 인덱스(uk_membership_periods_open, schema.sql)가 "같은
     * (fanUserId, artistId)에 열린 기간은 하나만" 규칙을 실제로 강제하고, 그 제약에 걸리면 이미
     * 원하는 상태(열린 기간 존재)가 달성된 것이므로 예외를 무시한다.
     */
    public void markActive(Long fanUserId, Long artistId, LocalDateTime newPeriodStartedAt) {
        membershipCache.put(fanUserId, artistId, true);
        if (newPeriodStartedAt != null) {
            try {
                membershipPeriodRepository.save(MembershipPeriod.start(fanUserId, artistId, newPeriodStartedAt));
            } catch (DataIntegrityViolationException e) {
                // 이미 열린 기간이 있음 — 동시/재시도로 활성화 이벤트가 겹쳐 들어온 경우, 원하는 결과가
                // 이미 달성돼 있으므로 무시한다.
            }
        }
    }

    /**
     * community-service의 자정 배치가 멤버십을 만료시킨 직후 호출 — 캐시된 true를 즉시 정정하고,
     * 열려있던 기간 이력을 닫고, 그 팬이 지금 접속 중이면(화면을 이미 켜놓고 있었다면) 만료 알림을
     * 한 번 보낸다. 접속 중이 아니면 그냥 버려진다(별도 저장/재전송 안 함) — 다음에 REST로 이력을
     * 조회하면 403으로 걸러진다.
     */
    public void markExpired(Long fanUserId, Long artistId, LocalDateTime periodEndedAt) {
        membershipCache.put(fanUserId, artistId, false);
        if (periodEndedAt != null) {
            membershipPeriodRepository.findOpenPeriod(fanUserId, artistId)
                    .ifPresent(period -> {
                        period.close(periodEndedAt);
                        membershipPeriodRepository.save(period);
                    });
        }
        broadcastPublisher.toUser(
                String.valueOf(fanUserId),
                "/queue/rooms/" + artistId + "/notice",
                MembershipExpiredNotice.of(artistId)
        );
    }
}
