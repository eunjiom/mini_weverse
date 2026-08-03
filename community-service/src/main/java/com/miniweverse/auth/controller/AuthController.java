package com.miniweverse.auth.controller;

import com.miniweverse.auth.dto.LoginRequest;
import com.miniweverse.auth.dto.SignupRequest;
import com.miniweverse.auth.dto.SignupResponse;
import com.miniweverse.auth.dto.TokenResponse;
import com.miniweverse.auth.service.AuthService;
import com.miniweverse.auth.service.LoginResult;
import com.miniweverse.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "인증", description = "회원가입, 로그인, 토큰 재발급, 로그아웃")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "회원가입", description = "이메일/비밀번호로 신규 계정을 생성합니다.")
    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        User user = authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new SignupResponse(user.getId(), user.getNickname()));
    }

    @Operation(summary = "로그인", description = "이메일/비밀번호로 로그인합니다. 일반 유저는 JWT 액세스/리프레시 토큰을 쿠키와 Authorization 헤더로 내려받고, 관리자 계정은 세션 기반 인증으로 처리됩니다.")
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        LoginResult result = authService.login(request.email(), request.password());

        if (result instanceof LoginResult.UserLoginResult userLogin) {
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, userLogin.refreshTokenCookie().toString())
                    .header(HttpHeaders.SET_COOKIE, userLogin.accessTokenCookie().toString())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + userLogin.accessToken())
                    .body(new TokenResponse(userLogin.accessToken()));
        }

        establishAdminSession((LoginResult.AdminLoginResult) result, httpRequest);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "액세스 토큰 재발급", description = "쿠키에 담긴 리프레시 토큰으로 새 액세스/리프레시 토큰을 발급합니다.")
    @PostMapping("/reissue")
    public ResponseEntity<TokenResponse> reissue(
            @CookieValue(name = "refreshToken", required = false) String refreshToken
    ) {
        LoginResult.UserLoginResult result = authService.reissue(refreshToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, result.refreshTokenCookie().toString())
                .header(HttpHeaders.SET_COOKIE, result.accessTokenCookie().toString())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + result.accessToken())
                .body(new TokenResponse(result.accessToken()));
    }

    @Operation(summary = "로그아웃", description = "관리자는 세션을, 일반 유저는 리프레시 토큰을 무효화하고 인증 쿠키를 만료시킵니다.")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = "refreshToken", required = false) String refreshToken,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        response.addHeader(HttpHeaders.SET_COOKIE, authService.expiredRefreshTokenCookie().toString());
        response.addHeader(HttpHeaders.SET_COOKIE, authService.expiredAccessTokenCookie().toString());

        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY) != null) {
            session.invalidate();
            return ResponseEntity.ok().build();
        }

        if (refreshToken != null && !refreshToken.isBlank()) {
            authService.logoutByRefreshToken(refreshToken);
        }
        return ResponseEntity.ok().build();
    }

    private void establishAdminSession(LoginResult.AdminLoginResult adminLogin, HttpServletRequest request) {
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                adminLogin.user().getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        HttpSession session = request.getSession(true);
        request.changeSessionId();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
    }
}
