package com.miniweverse.follow.entity;

import com.miniweverse.exception.AuthUserExceptions.InvalidRequestException;
import com.miniweverse.exception.AuthUserExceptions.NotArtistException;
import com.miniweverse.exception.AuthUserExceptions.SelfFollowNotAllowedException;
import com.miniweverse.user.entity.User;
import com.miniweverse.user.enums.Role;
import java.util.Objects;
import lombok.Getter;

/**
 * follower/artist 조합 자체가 이 관계의 유일한 식별자라, 별도 영속성 없이
 * 팔로우 생성 규칙만 검증하는 순수 도메인 객체다. 실제 저장/조회는 FollowRepository가
 * (follower_id, artist_id)를 직접 다루는 네이티브 쿼리로 처리한다.
 */
@Getter
public class Follow {

    private final User follower;
    private final User artist;

    private Follow(User follower, User artist) {
        this.follower = follower;
        this.artist = artist;
    }

    public static Follow create(User follower, User artist) {
        if (follower == null || artist == null) {
            throw new InvalidRequestException("follower와 artist는 필수입니다.");
        }
        boolean isSelfFollow = follower == artist
                || (follower.getId() != null && Objects.equals(follower.getId(), artist.getId()));
        if (isSelfFollow) {
            throw new SelfFollowNotAllowedException();
        }
        if (artist.getRole() != Role.ARTIST) {
            throw new NotArtistException();
        }
        return new Follow(follower, artist);
    }
}
