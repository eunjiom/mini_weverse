package com.miniweverse.user.dto;

import com.miniweverse.follow.dto.FollowedArtistResponse;
import java.util.List;

/**
 * followingArtists는 본인 프로필을 조회할 때만 채워진다(null이면 본인이 아님) — 팔로잉 목록은
 * 개인 취향 정보로 보고 본인만 볼 수 있게 한다. 팔로워/팔로잉 수는 누구나 볼 수 있다.
 */
public record UserProfileResponse(
        Long userId,
        String nickname,
        long followerCount,
        long followingCount,
        List<FollowedArtistResponse> followingArtists
) {
}
