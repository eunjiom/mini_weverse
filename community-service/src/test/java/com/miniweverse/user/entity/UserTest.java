package com.miniweverse.user.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.miniweverse.exception.AuthUserExceptions.InvalidRequestException;
import com.miniweverse.user.enums.AuthProvider;
import com.miniweverse.common.security.jwt.Role;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class UserTest {

    @Test
    void 로컬_가입은_비밀번호가_없으면_예외() {
        assertThatThrownBy(() -> User.createLocal("local@test.com", " ", "닉네임", Role.FAN))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void 카카오_가입은_providerId가_없으면_예외() {
        assertThatThrownBy(() -> User.createKakao("kakao@test.com", " ", "닉네임", Role.FAN))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void 정상_로컬_가입은_LOCAL_provider를_가진다() {
        User user = User.createLocal("local2@test.com", "password", "닉네임", Role.FAN);

        assertThat(user.getProvider()).isEqualTo(AuthProvider.LOCAL);
        assertThat(user.getPassword()).isEqualTo("password");
        assertThat(user.isDeleted()).isFalse();
    }

    @Test
    void 정상_카카오_가입은_비밀번호가_없다() {
        User user = User.createKakao("kakao2@test.com", "kakao-provider-id", "닉네임", Role.FAN);

        assertThat(user.getProvider()).isEqualTo(AuthProvider.KAKAO);
        assertThat(user.getPassword()).isNull();
    }

    @Test
    void delete_호출시_deletedAt이_설정된다() {
        User user = User.createLocal("delete@test.com", "password", "닉네임", Role.FAN);

        user.delete();

        assertThat(user.isDeleted()).isTrue();
    }
}
