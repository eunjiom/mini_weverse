package com.miniweverse.artist.dto;

import com.miniweverse.user.entity.ArtistProfile;
import com.miniweverse.user.enums.ArtistCategory;

public record ArtistProfileDetailResponse(
        Long artistId,
        String channelName,
        String introduction,
        ArtistCategory category,
        Long groupId,
        String groupChannelName,
        String profileImageUrl
) {
    public static ArtistProfileDetailResponse from(ArtistProfile artistProfile) {
        ArtistProfile group = artistProfile.getGroup();
        return new ArtistProfileDetailResponse(
                artistProfile.getId(),
                artistProfile.getChannelName(),
                artistProfile.getIntroduction(),
                artistProfile.getCategory(),
                group != null ? group.getId() : null,
                group != null ? group.getChannelName() : null,
                artistProfile.getProfileImageUrl()
        );
    }
}
