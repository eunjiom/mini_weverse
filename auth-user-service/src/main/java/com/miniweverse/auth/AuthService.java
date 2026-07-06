package com.miniweverse.auth;

import com.miniweverse.admin.entity.Admin;
import com.miniweverse.admin.repository.AdminRepository;
import com.miniweverse.auth.dto.SignupRequest;
import com.miniweverse.exception.AuthUserExceptions.DuplicateEmailException;
import com.miniweverse.exception.AuthUserExceptions.InvalidCredentialsException;
import com.miniweverse.exception.AuthUserExceptions.InvalidRefreshTokenException;
import com.miniweverse.user.entity.User;
import com.miniweverse.user.enums.Role;
import com.miniweverse.user.repository.UserRepository;
import io.jsonwebtoken.JwtException;
import java.time.Duration;
import java.util.Optional;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private static final String REFRESH_TOKEN_COOKIE_NAME = "refreshToken";

    private final UserRepository userRepository;
    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;

    public AuthService(
            UserRepository userRepository,
            AdminRepository adminRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider,
            RefreshTokenRepository refreshTokenRepository,
            JwtProperties jwtProperties
    ) {
        this.userRepository = userRepository;
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtProperties = jwtProperties;
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
        return userRepository.save(user);
    }

    public LoginResult login(String email, String rawPassword) {
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            validatePassword(rawPassword, user.getPassword());
            return issueUserLogin(user);
        }

        Admin admin = adminRepository.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);
        validatePassword(rawPassword, admin.getPassword());
        return new LoginResult.AdminLoginResult(admin);
    }

    public User findOrCreateKakaoUser(String providerId, String email, String nickname) {
        return userRepository.findByProviderId(providerId)
                .orElseGet(() -> userRepository.save(
                        User.createKakao(resolveEmail(email, providerId), providerId, nickname, Role.FAN)));
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
                .path("/")
                .maxAge(0)
                .sameSite("Lax")
                .build();
    }

    LoginResult.UserLoginResult issueUserLogin(User user) {
        String accessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getNickname(), user.getRole());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId());
        refreshTokenRepository.save(user.getId(), refreshToken);
        return new LoginResult.UserLoginResult(accessToken, refreshTokenCookie(refreshToken));
    }

    private Long extractUserId(String refreshTokenCookieValue) {
        if (refreshTokenCookieValue == null || refreshTokenCookieValue.isBlank()) {
            throw new InvalidRefreshTokenException();
        }
        try {
            return Long.valueOf(jwtTokenProvider.parseClaims(refreshTokenCookieValue).getSubject());
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

    private ResponseCookie refreshTokenCookie(String refreshToken) {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, refreshToken)
                .httpOnly(true)
                .path("/")
                .maxAge(Duration.ofMillis(jwtProperties.refreshTokenValidity()))
                .sameSite("Lax")
                .build();
    }

    private void validatePassword(String rawPassword, String encodedPassword) {
        if (!passwordEncoder.matches(rawPassword, encodedPassword)) {
            throw new InvalidCredentialsException();
        }
    }
}
