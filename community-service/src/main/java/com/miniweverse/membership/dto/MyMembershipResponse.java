package com.miniweverse.membership.dto;

import com.miniweverse.membership.entity.Membership;
import java.time.LocalDateTime;

public record MyMembershipResponse(
        Long membershipId,
        Long artistId,
        String artistNickname,
        String status,
        LocalDateTime expiresAt
) {
    public static MyMembershipResponse from(Membership membership) {
        return new MyMembershipResponse(
                membership.getId(),
                membership.getArtist().getId(),
                membership.getArtist().getNickname(),
                membership.getStatus().name(),
                membership.getExpiresAt()
        );
    }
}
