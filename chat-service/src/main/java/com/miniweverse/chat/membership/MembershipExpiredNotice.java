package com.miniweverse.chat.membership;

public record MembershipExpiredNotice(Long artistId, String message) {

    public static MembershipExpiredNotice of(Long artistId) {
        return new MembershipExpiredNotice(artistId, "멤버십이 만료되어 더 이상 이 채팅을 볼 수 없습니다.");
    }
}
