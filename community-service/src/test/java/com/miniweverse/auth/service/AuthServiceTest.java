package com.miniweverse.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.miniweverse.auth.jwt.JwtProperties;
import com.miniweverse.auth.jwt.JwtTokenProvider;
import com.miniweverse.auth.repository.RefreshTokenRepository;
import com.miniweverse.common.security.jwt.JwtVerifier;
import com.miniweverse.common.security.jwt.Role;
import com.miniweverse.exception.AuthUserExceptions.InvalidRefreshTokenException;
import com.miniweverse.user.entity.User;
import com.miniweverse.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final Long USER_ID = 1L;

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private JwtVerifier jwtVerifier;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private AuthService authService;
    private User user;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties = new JwtProperties("unused", 1_800_000L, 604_800_000L);
        authService = new AuthService(
                userRepository, passwordEncoder, jwtTokenProvider, jwtVerifier, refreshTokenRepository, jwtProperties, true
        );
        user = User.createLocal("user@test.com", "encoded", "닉네임", Role.FAN);
        setId(user, USER_ID);
    }

    @Test
    void 저장된_RT와_요청한_RT가_일치하면_새_토큰을_발급하고_RT를_교체한다() {
        String oldRefreshToken = "old-refresh-token";
        given(jwtVerifier.parseClaims(oldRefreshToken)).willReturn(refreshClaims(USER_ID));
        given(refreshTokenRepository.findRefreshToken(USER_ID)).willReturn(Optional.of(oldRefreshToken));
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(jwtTokenProvider.createAccessToken(anyLong(), anyString(), eq(Role.FAN))).willReturn("new-access-token");
        given(jwtTokenProvider.createRefreshToken(USER_ID)).willReturn("new-refresh-token");

        LoginResult.UserLoginResult result = authService.reissue(oldRefreshToken);

        assertThat(result.accessToken()).isEqualTo("new-access-token");
        verify(refreshTokenRepository).save(USER_ID, "new-refresh-token");
        verify(refreshTokenRepository, never()).delete(USER_ID);
    }

    @Test
    void 저장된_RT와_요청한_RT가_다르면_저장된_RT를_삭제하고_예외를_던진다() {
        String reusedOldToken = "already-rotated-token";
        String currentStoredToken = "current-token";
        given(jwtVerifier.parseClaims(reusedOldToken)).willReturn(refreshClaims(USER_ID));
        given(refreshTokenRepository.findRefreshToken(USER_ID)).willReturn(Optional.of(currentStoredToken));

        assertThatThrownBy(() -> authService.reissue(reusedOldToken))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verify(refreshTokenRepository).delete(USER_ID);
        verify(jwtTokenProvider, never()).createAccessToken(anyLong(), anyString(), eq(Role.FAN));
    }

    @Test
    void Redis에_저장된_RT_자체가_없으면_삭제_후_예외를_던진다() {
        String refreshToken = "some-token";
        given(jwtVerifier.parseClaims(refreshToken)).willReturn(refreshClaims(USER_ID));
        given(refreshTokenRepository.findRefreshToken(USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.reissue(refreshToken))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verify(refreshTokenRepository).delete(USER_ID);
    }

    private Claims refreshClaims(Long userId) {
        return Jwts.claims()
                .subject(String.valueOf(userId))
                .add(JwtVerifier.CLAIM_TOKEN_TYPE, JwtTokenProvider.TOKEN_TYPE_REFRESH)
                .build();
    }

    private void setId(User user, Long id) {
        try {
            var field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
