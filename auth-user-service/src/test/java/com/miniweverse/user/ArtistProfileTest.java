package com.miniweverse.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.miniweverse.exception.InvalidArtistProfileException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class ArtistProfileTest {

    @Test
    void FAN은_아티스트_프로필을_만들_수_없다() {
        User fan = User.createLocal("fan@test.com", "pw", "fan", Role.FAN);

        assertThatThrownBy(() -> ArtistProfile.create(fan, "channel", null, ArtistCategory.SOLO, null, null))
                .isInstanceOf(InvalidArtistProfileException.class);
    }

    @Test
    void SOLO는_그룹을_가질_수_없다() {
        User artist = User.createLocal("artist@test.com", "pw", "artist", Role.ARTIST);
        ArtistProfile group = createGroup("group@test.com");

        assertThatThrownBy(() -> ArtistProfile.create(artist, "channel", null, ArtistCategory.SOLO, group, null))
                .isInstanceOf(InvalidArtistProfileException.class);
    }

    @Test
    void GROUP은_그룹을_가질_수_없다() {
        User artist = User.createLocal("artist2@test.com", "pw", "artist2", Role.ARTIST);
        ArtistProfile group = createGroup("group2@test.com");

        assertThatThrownBy(() -> ArtistProfile.create(artist, "channel", null, ArtistCategory.GROUP, group, null))
                .isInstanceOf(InvalidArtistProfileException.class);
    }

    @Test
    void MEMBER는_그룹이_없으면_예외() {
        User artist = User.createLocal("artist3@test.com", "pw", "artist3", Role.ARTIST);

        assertThatThrownBy(() -> ArtistProfile.create(artist, "channel", null, ArtistCategory.MEMBER, null, null))
                .isInstanceOf(InvalidArtistProfileException.class);
    }

    @Test
    void MEMBER는_그룹이_GROUP_카테고리가_아니면_예외() {
        User artist = User.createLocal("artist4@test.com", "pw", "artist4", Role.ARTIST);
        User soloUser = User.createLocal("solo@test.com", "pw", "solo", Role.ARTIST);
        ArtistProfile solo = ArtistProfile.create(soloUser, "solo-channel", null, ArtistCategory.SOLO, null, null);

        assertThatThrownBy(() -> ArtistProfile.create(artist, "channel", null, ArtistCategory.MEMBER, solo, null))
                .isInstanceOf(InvalidArtistProfileException.class);
    }

    @Test
    void 정상_MEMBER_생성시_그룹이_설정된다() {
        ArtistProfile group = createGroup("group3@test.com");
        User memberUser = User.createLocal("member@test.com", "pw", "member", Role.ARTIST);

        ArtistProfile member = ArtistProfile.create(memberUser, "member-channel", null, ArtistCategory.MEMBER, group, null);

        assertThat(member.getGroup()).isEqualTo(group);
        assertThat(member.getCategory()).isEqualTo(ArtistCategory.MEMBER);
    }

    private ArtistProfile createGroup(String email) {
        User groupUser = User.createLocal(email, "pw", "group", Role.ARTIST);
        return ArtistProfile.create(groupUser, "group-channel", null, ArtistCategory.GROUP, null, null);
    }
}
