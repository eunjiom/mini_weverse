package com.miniweverse.chat.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;

import com.miniweverse.common.security.jwt.JwtVerifier;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.SignatureException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class JwtHandshakeInterceptorTest {

    @Mock
    private JwtVerifier jwtVerifier;

    private JwtHandshakeInterceptor interceptor;
    private MockHttpServletResponse mockResponse;
    private Map<String, Object> attributes;

    @BeforeEach
    void setUp() {
        interceptor = new JwtHandshakeInterceptor(jwtVerifier);
        mockResponse = new MockHttpServletResponse();
        attributes = new HashMap<>();
    }

    @Test
    void accessToken_쿠키가_없으면_401로_거부한다() {
        ServerHttpRequest request = requestWithCookie(null);

        boolean result = interceptor.beforeHandshake(request, response(), null, attributes);

        assertThat(result).isFalse();
        assertThat(mockResponse.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    void FAN_역할의_유효한_토큰이면_통과하고_속성에_userId_role을_담는다() {
        given(jwtVerifier.parseClaims("valid-token")).willReturn(accessClaims("1", "FAN"));
        ServerHttpRequest request = requestWithCookie("valid-token");

        boolean result = interceptor.beforeHandshake(request, response(), null, attributes);

        assertThat(result).isTrue();
        assertThat(attributes.get(JwtHandshakeInterceptor.USER_ID_ATTRIBUTE)).isEqualTo(1L);
        assertThat(attributes.get(JwtHandshakeInterceptor.ROLE_ATTRIBUTE)).isEqualTo(com.miniweverse.common.security.jwt.Role.FAN);
    }

    @Test
    void ADMIN_역할은_채팅_핸드셰이크_허용_대상이_아니라_403으로_거부한다() {
        given(jwtVerifier.parseClaims("admin-token")).willReturn(accessClaims("1", "ADMIN"));
        ServerHttpRequest request = requestWithCookie("admin-token");

        boolean result = interceptor.beforeHandshake(request, response(), null, attributes);

        assertThat(result).isFalse();
        assertThat(mockResponse.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
    }

    @Test
    void refresh_타입_토큰이면_401로_거부한다() {
        Claims refreshClaims = Jwts.claims().subject("1").add(JwtVerifier.CLAIM_TOKEN_TYPE, "refresh").add("role", "FAN").build();
        given(jwtVerifier.parseClaims("refresh-token")).willReturn(refreshClaims);
        ServerHttpRequest request = requestWithCookie("refresh-token");

        boolean result = interceptor.beforeHandshake(request, response(), null, attributes);

        assertThat(result).isFalse();
        assertThat(mockResponse.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    void 위조된_토큰이면_401로_거부한다() {
        willThrow(new SignatureException("bad signature")).given(jwtVerifier).parseClaims("forged-token");
        ServerHttpRequest request = requestWithCookie("forged-token");

        boolean result = interceptor.beforeHandshake(request, response(), null, attributes);

        assertThat(result).isFalse();
        assertThat(mockResponse.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    private Claims accessClaims(String subject, String role) {
        return Jwts.claims()
                .subject(subject)
                .add(JwtVerifier.CLAIM_TOKEN_TYPE, JwtVerifier.TOKEN_TYPE_ACCESS)
                .add("role", role)
                .build();
    }

    private ServerHttpRequest requestWithCookie(String accessTokenValue) {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        if (accessTokenValue != null) {
            servletRequest.setCookies(new jakarta.servlet.http.Cookie("accessToken", accessTokenValue));
        }
        return new ServletServerHttpRequest(servletRequest);
    }

    private ServerHttpResponse response() {
        return new ServletServerHttpResponse(mockResponse);
    }
}
