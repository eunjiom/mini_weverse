package com.miniweverse.artist.dto;

import com.miniweverse.user.entity.ArtistProfile;
import com.miniweverse.user.enums.ArtistCategory;

public record ArtistSearchResponse(
        Long artistId,
        String channelName,
        ArtistCategory category,
        String profileImageUrl
) {
    public static ArtistSearchResponse from(ArtistProfile artistProfile) {
        return new ArtistSearchResponse(
                artistProfile.getId(),
                artistProfile.getChannelName(),
                artistProfile.getCategory(),
                artistProfile.getProfileImageUrl()
        );
    }
}
