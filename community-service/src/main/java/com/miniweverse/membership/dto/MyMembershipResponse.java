package com.miniweverse.membership.dto;

import com.miniweverse.membership.entity.Membership;
import com.miniweverse.user.entity.User;
import java.time.LocalDateTime;

public record MyMembershipResponse(
        Long membershipId,
        Long artistId,
        String artistNickname,
        String status,
        LocalDateTime expiresAt
) {
    private static final String WITHDRAWN_ARTIST_NICKNAME = "탈퇴한 아티스트";

    public static MyMembershipResponse from(Membership membership) {
        User artist = membership.getArtist();
        return new MyMembershipResponse(
                membership.getId(),
                artist != null ? artist.getId() : null,
                artist != null ? artist.getNickname() : WITHDRAWN_ARTIST_NICKNAME,
                membership.getStatus().name(),
                membership.getExpiresAt()
        );
    }
}
