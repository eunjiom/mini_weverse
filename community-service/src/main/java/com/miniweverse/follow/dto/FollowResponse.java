package com.miniweverse.follow.dto;

import com.miniweverse.follow.entity.Follow;

public record FollowResponse(
        Long followId,
        Long artistId
) {
    public static FollowResponse from(Follow follow) {
        return new FollowResponse(follow.getId(), follow.getArtist().getId());
    }
}
