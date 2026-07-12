package com.miniweverse.membership.dto;

import com.miniweverse.membership.entity.Membership;
import java.time.LocalDateTime;

public record MembershipResponse(
        Long membershipId,
        Long artistId,
        String status,
        LocalDateTime expiresAt
) {
    public static MembershipResponse from(Membership membership) {
        return new MembershipResponse(
                membership.getId(),
                membership.getArtist().getId(),
                membership.getStatus().name(),
                membership.getExpiresAt()
        );
    }
}
