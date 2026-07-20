package com.miniweverse.chat.websocket;

import com.miniweverse.auth.jwt.JwtTokenProvider;
import com.miniweverse.common.security.jwt.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.Cookie;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * WebSocket 핸드셰이크 요청은 브라우저가 Authorization 헤더를 못 붙이므로, 로그인 시 함께
 * 발급되는 accessToken 쿠키에서 JWT를 읽어 검증한다. SecurityConfig의 서블릿 필터 체인과는
 * 별개 경로 — 이 인터셉터가 인증 실패 시 핸드셰이크 자체를 거부한다.
 */
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    public static final String USER_ID_ATTRIBUTE = "userId";
    public static final String ROLE_ATTRIBUTE = "role";

    private static final String ACCESS_TOKEN_COOKIE_NAME = "accessToken";
    private static final Set<Role> ALLOWED_ROLES = EnumSet.of(Role.FAN, Role.ARTIST);

    private final JwtTokenProvider jwtTokenProvider;

    public JwtHandshakeInterceptor(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes
    ) {
        String token = extractCookieToken(request);
        if (token == null) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        try {
            Claims claims = jwtTokenProvider.parseClaims(token);
            if (!JwtTokenProvider.TOKEN_TYPE_ACCESS.equals(claims.get(JwtTokenProvider.CLAIM_TOKEN_TYPE, String.class))) {
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }
            String roleClaim = claims.get("role", String.class);
            if (roleClaim == null) {
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }
            Role role = Role.valueOf(roleClaim);
            if (!ALLOWED_ROLES.contains(role)) {
                response.setStatusCode(HttpStatus.FORBIDDEN);
                return false;
            }
            attributes.put(USER_ID_ATTRIBUTE, Long.valueOf(claims.getSubject()));
            attributes.put(ROLE_ATTRIBUTE, role);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception
    ) {
        // no-op
    }

    private String extractCookieToken(ServerHttpRequest request) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            return null;
        }
        Cookie[] cookies = servletRequest.getServletRequest().getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (ACCESS_TOKEN_COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
