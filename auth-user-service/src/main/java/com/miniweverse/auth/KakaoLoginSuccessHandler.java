package com.miniweverse.auth;

import tools.jackson.databind.ObjectMapper;
import com.miniweverse.auth.dto.TokenResponse;
import com.miniweverse.user.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/**
 * 카카오 로그인 성공 후, 프론트엔드가 아직 없어서 리다이렉트 대신 AT를 JSON으로 바로 응답한다.
 * (프론트 생기면 리다이렉트 + 쿼리 파라미터/쿠키 방식으로 바꿔야 할 수 있음)
 */
@Component
public class KakaoLoginSuccessHandler implements AuthenticationSuccessHandler {

    private final AuthService authService;
    private final ObjectMapper objectMapper;

    public KakaoLoginSuccessHandler(AuthService authService, ObjectMapper objectMapper) {
        this.authService = authService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String providerId = String.valueOf(oAuth2User.getAttributes().get("id"));

        @SuppressWarnings("unchecked")
        Map<String, Object> kakaoAccount = (Map<String, Object>) oAuth2User.getAttributes().get("kakao_account");
        @SuppressWarnings("unchecked")
        Map<String, Object> profile = kakaoAccount != null
                ? (Map<String, Object>) kakaoAccount.get("profile")
                : null;
        String nickname = profile != null ? String.valueOf(profile.get("nickname")) : "카카오유저";
        String email = kakaoAccount != null ? (String) kakaoAccount.get("email") : null;

        User user = authService.findOrCreateKakaoUser(providerId, email, nickname);
        LoginResult.UserLoginResult result = authService.issueUserLogin(user);

        response.addHeader(HttpHeaders.SET_COOKIE, result.refreshTokenCookie().toString());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(new TokenResponse(result.accessToken())));
    }
}
