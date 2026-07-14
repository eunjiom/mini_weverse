package com.miniweverse.follow.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.miniweverse.exception.AuthUserExceptions.SelfFollowNotAllowedException;
import com.miniweverse.user.entity.ArtistProfile;
import com.miniweverse.user.entity.User;
import com.miniweverse.user.enums.ArtistCategory;
import com.miniweverse.user.enums.Role;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * artist가 User에서 ArtistProfile로 바뀌면서, ArtistProfile.create() 자체가 이미 소유주의
 * Role.ARTIST를 강제한다 — "ARTIST가 아닌 유저를 감싼 ArtistProfile"은 정상 생성 경로로는
 * 애초에 만들 수 없어져서, Follow.create()의 NotArtistException 케이스는 더 이상 이 레벨에서
 * 재현 가능한 시나리오가 아니다(그래서 관련 테스트를 제거했다 — ArtistProfileTest가 그 불변식을 검증한다).
 */
@Tag("unit")
class FollowTest {

    @Test
    void 자기_자신은_팔로우할_수_없다() {
        User user = User.createLocal("self@test.com", "pw", "self", Role.ARTIST);
        ArtistProfile ownProfile = ArtistProfile.create(user, "channel", null, ArtistCategory.SOLO, null, null);

        assertThatThrownBy(() -> Follow.create(user, ownProfile))
                .isInstanceOf(SelfFollowNotAllowedException.class);
    }

    @Test
    void 정상_팔로우_생성() {
        User follower = User.createLocal("follower2@test.com", "pw", "follower2", Role.FAN);
        User artistUser = User.createLocal("artist@test.com", "pw", "artist", Role.ARTIST);
        ArtistProfile artistProfile = ArtistProfile.create(artistUser, "channel", null, ArtistCategory.SOLO, null, null);

        Follow follow = Follow.create(follower, artistProfile);

        assertThat(follow.getFollower()).isEqualTo(follower);
        assertThat(follow.getArtist()).isEqualTo(artistProfile);
    }
}
