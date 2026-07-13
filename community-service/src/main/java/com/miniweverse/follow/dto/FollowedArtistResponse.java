package com.miniweverse.follow.dto;

public record FollowedArtistResponse(
        Long artistId,
        String nickname,
        String category
) {
}
