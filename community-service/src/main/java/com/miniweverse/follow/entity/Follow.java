package com.miniweverse.follow.entity;

import com.miniweverse.exception.AuthUserExceptions.InvalidRequestException;
import com.miniweverse.exception.AuthUserExceptions.NotArtistException;
import com.miniweverse.exception.AuthUserExceptions.SelfFollowNotAllowedException;
import com.miniweverse.user.entity.ArtistProfile;
import com.miniweverse.user.entity.User;
import com.miniweverse.user.enums.Role;
import java.util.Objects;
import lombok.Getter;

/**
 * follower/artist 조합 자체가 이 관계의 유일한 식별자라, 별도 영속성 없이
 * 팔로우 생성 규칙만 검증하는 순수 도메인 객체다. 실제 저장/조회는 FollowRepository가
 * (follower_id, artist_id)를 직접 다루는 네이티브 쿼리로 처리한다.
 * artist는 User가 아니라 ArtistProfile을 가리킨다 — ArtistProfile은 소프트 삭제 후 재생성이
 * 가능한 반면 User는 계정 생명주기 동안 고정이라, "이 채널"이 아니라 "이 사람"을 팔로우한다는
 * 의미로 이 프로젝트에서는 결국 User PK가 아닌 ArtistProfile PK를 그대로 식별자로 쓰기로 했다
 * (User 경유 시 매 요청마다 findByUser 간접조회가 필요했던 문제도 같이 해소됨).
 */
@Getter
public class Follow {

    private final User follower;
    private final ArtistProfile artist;

    private Follow(User follower, ArtistProfile artist) {
        this.follower = follower;
        this.artist = artist;
    }

    public static Follow create(User follower, ArtistProfile artist) {
        if (follower == null || artist == null) {
            throw new InvalidRequestException("follower와 artist는 필수입니다.");
        }
        User artistUser = artist.getUser();
        if (artistUser == null) {
            throw new InvalidRequestException("아티스트 정보를 찾을 수 없습니다.");
        }
        boolean isSelfFollow = follower == artistUser
                || (follower.getId() != null && Objects.equals(follower.getId(), artistUser.getId()));
        if (isSelfFollow) {
            throw new SelfFollowNotAllowedException();
        }
        if (artistUser.getRole() != Role.ARTIST) {
            throw new NotArtistException();
        }
        return new Follow(follower, artist);
    }
}
