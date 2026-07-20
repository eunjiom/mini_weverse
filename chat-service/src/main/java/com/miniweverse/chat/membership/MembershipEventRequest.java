package com.miniweverse.chat.membership;

import java.time.LocalDateTime;

/** periodBoundaryAt: 활성화면 새로 연 기간의 시작 시각(그냥 연장이면 null), 만료면 닫은 기간의 종료 시각. */
public record MembershipEventRequest(Long fanUserId, Long artistId, LocalDateTime periodBoundaryAt) {
}
