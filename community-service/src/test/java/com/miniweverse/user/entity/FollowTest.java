package com.miniweverse.user.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.miniweverse.exception.AuthUserExceptions.NotArtistException;
import com.miniweverse.exception.AuthUserExceptions.SelfFollowNotAllowedException;
import com.miniweverse.user.enums.Role;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class FollowTest {

    @Test
    void 자기_자신은_팔로우할_수_없다() {
        User user = User.createLocal("self@test.com", "pw", "self", Role.ARTIST);

        assertThatThrownBy(() -> Follow.create(user, user))
                .isInstanceOf(SelfFollowNotAllowedException.class);
    }

    @Test
    void ARTIST가_아니면_팔로우_대상이_될_수_없다() {
        User follower = User.createLocal("follower@test.com", "pw", "follower", Role.FAN);
        User notArtist = User.createLocal("notartist@test.com", "pw", "notartist", Role.FAN);

        assertThatThrownBy(() -> Follow.create(follower, notArtist))
                .isInstanceOf(NotArtistException.class);
    }

    @Test
    void 정상_팔로우_생성() {
        User follower = User.createLocal("follower2@test.com", "pw", "follower2", Role.FAN);
        User artist = User.createLocal("artist@test.com", "pw", "artist", Role.ARTIST);

        Follow follow = Follow.create(follower, artist);

        assertThat(follow.getFollower()).isEqualTo(follower);
        assertThat(follow.getArtist()).isEqualTo(artist);
    }
}
