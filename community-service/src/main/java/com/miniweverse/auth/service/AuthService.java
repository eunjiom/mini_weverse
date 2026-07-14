package com.miniweverse.auth.service;

import com.miniweverse.auth.dto.SignupRequest;
import com.miniweverse.auth.jwt.JwtProperties;
import com.miniweverse.auth.jwt.JwtTokenProvider;
import com.miniweverse.auth.repository.RefreshTokenRepository;
import com.miniweverse.exception.AuthUserExceptions.DuplicateEmailException;
import com.miniweverse.exception.AuthUserExceptions.InvalidCredentialsException;
import com.miniweverse.exception.AuthUserExceptions.InvalidRefreshTokenException;
import com.miniweverse.user.entity.User;
import com.miniweverse.user.enums.AuthProvider;
import com.miniweverse.user.enums.Role;
import com.miniweverse.user.repository.UserRepository;
import io.jsonwebtoken.JwtException;
import java.time.Duration;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private static final String REFRESH_TOKEN_COOKIE_NAME = "refreshToken";
    private static final String ACCESS_TOKEN_COOKIE_NAME = "accessToken";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;
    private final boolean cookieSecure;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider,
            RefreshTokenRepository refreshTokenRepository,
            JwtProperties jwtProperties,
            @Value("${cookie.secure:true}") boolean cookieSecure
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtProperties = jwtProperties;
        this.cookieSecure = cookieSecure;
    }

    public User signup(SignupRequest request) {
        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw new DuplicateEmailException();
        }
        User user = User.createLocal(
                request.email(),
                passwordEncoder.encode(request.password()),
                request.nickname(),
                Role.FAN
        );
        try {
            return userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            // 동시 요청으로 findByEmail 체크를 통과한 뒤 DB 유니크 제약에서 걸린 경우.
            throw new DuplicateEmailException();
        }
    }

    public LoginResult login(String email, String rawPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);
        validatePassword(rawPassword, user.getPassword());

        if (user.getRole() == Role.ADMIN) {
            return new LoginResult.AdminLoginResult(user);
        }
        return issueUserLogin(user);
    }

    public User findOrCreateKakaoUser(String providerId, String email, String nickname) {
        Optional<User> existing = userRepository.findByProviderAndProviderId(AuthProvider.KAKAO, providerId);
        if (existing.isPresent()) {
            return existing.get();
        }
        User newUser = User.createKakao(resolveEmail(email, providerId), providerId, nickname, Role.FAN);
        try {
            return userRepository.save(newUser);
        } catch (DataIntegrityViolationException e) {
            // 동시 카카오 로그인으로 다른 요청이 먼저 만든 경우, 그 유저를 재사용한다.
            return userRepository.findByProviderAndProviderId(AuthProvider.KAKAO, providerId)
                    .orElseThrow(() -> e);
        }
    }

    /**
     * RT는 Redis에 저장된 값과 정확히 일치할 때만 재발급한다.
     * 불일치(탈취 의심 또는 이미 Rotation된 RT 재사용)면 저장된 RT까지 지우고 재로그인을 요구한다.
     */
    public LoginResult.UserLoginResult reissue(String refreshTokenCookieValue) {
        Long userId = extractUserId(refreshTokenCookieValue);

        String storedToken = refreshTokenRepository.findRefreshToken(userId).orElse(null);
        if (storedToken == null || !storedToken.equals(refreshTokenCookieValue)) {
            refreshTokenRepository.delete(userId);
            throw new InvalidRefreshTokenException();
        }

        User user = userRepository.findById(userId).orElseThrow(InvalidRefreshTokenException::new);
        return issueUserLogin(user);
    }

    public void logoutByRefreshToken(String refreshTokenCookieValue) {
        try {
            Long userId = extractUserId(refreshTokenCookieValue);
            refreshTokenRepository.delete(userId);
        } catch (InvalidRefreshTokenException ignored) {
            // 이미 만료/위조된 토큰이어도 로그아웃 자체는 성공 처리한다.
        }
    }

    public ResponseCookie expiredRefreshTokenCookie() {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/")
                .maxAge(0)
                .sameSite("Lax")
                .build();
    }

    public ResponseCookie expiredAccessTokenCookie() {
        return ResponseCookie.from(ACCESS_TOKEN_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/")
                .maxAge(0)
                .sameSite("Lax")
                .build();
    }

    public LoginResult.UserLoginResult issueUserLogin(User user) {
        String accessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getNickname(), user.getRole());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId());
        refreshTokenRepository.save(user.getId(), refreshToken);
        return new LoginResult.UserLoginResult(accessToken, accessTokenCookie(accessToken), refreshTokenCookie(refreshToken));
    }

    private Long extractUserId(String refreshTokenCookieValue) {
        if (refreshTokenCookieValue == null || refreshTokenCookieValue.isBlank()) {
            throw new InvalidRefreshTokenException();
        }
        try {
            var claims = jwtTokenProvider.parseClaims(refreshTokenCookieValue);
            if (!JwtTokenProvider.TOKEN_TYPE_REFRESH.equals(claims.get(JwtTokenProvider.CLAIM_TOKEN_TYPE, String.class))) {
                throw new InvalidRefreshTokenException();
            }
            return Long.valueOf(claims.getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidRefreshTokenException();
        }
    }

    private String resolveEmail(String email, String providerId) {
        if (email != null && !email.isBlank()) {
            return email;
        }
        return "kakao_" + providerId + "@miniweverse.local";
    }

    /**
     * WebSocket 핸드셰이크 요청은 브라우저가 Authorization 헤더를 커스텀으로 못 붙이므로,
     * 게이트웨이/chat-service가 쿠키에서 AT를 읽어 인가할 수 있도록 헤더와 별개로 쿠키로도 내려준다.
     */
    private ResponseCookie accessTokenCookie(String accessToken) {
        return ResponseCookie.from(ACCESS_TOKEN_COOKIE_NAME, accessToken)
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/")
                .maxAge(Duration.ofMillis(jwtProperties.accessTokenValidity()))
                .sameSite("Lax")
                .build();
    }

    private ResponseCookie refreshTokenCookie(String refreshToken) {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, refreshToken)
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/")
                .maxAge(Duration.ofMillis(jwtProperties.refreshTokenValidity()))
                .sameSite("Lax")
                .build();
    }

    private void validatePassword(String rawPassword, String encodedPassword) {
        if (encodedPassword == null || !passwordEncoder.matches(rawPassword, encodedPassword)) {
            throw new InvalidCredentialsException();
        }
    }
}
