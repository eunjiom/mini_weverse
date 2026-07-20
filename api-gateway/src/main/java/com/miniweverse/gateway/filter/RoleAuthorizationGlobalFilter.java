package com.miniweverse.gateway.filter;

import com.miniweverse.common.response.ApiResponse;
import com.miniweverse.gateway.authz.AccessRule;
import com.miniweverse.gateway.authz.AccessType;
import com.miniweverse.gateway.authz.RouteAccessPolicy;
import com.miniweverse.gateway.exception.GatewayErrorCode;
import com.miniweverse.gateway.jwt.GatewayJwtVerifier;
import com.miniweverse.common.security.jwt.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

/**
 * 경로별 인가(RouteAccessPolicy)를 판단해 JWT 서명 검증 + role 체크를 수행하는 전역 필터.
 * 신원 식별(SecurityContext 구성)은 각 서비스가 자체 JwtAuthenticationFilter로 다시 하므로,
 * 여기서는 X-User-Id 같은 헤더를 만들어 넘기지 않는다 — 순수하게 "이 경로에 들어갈 수 있는지"만 판단.
 */
@Component
public class RoleAuthorizationGlobalFilter implements GlobalFilter, Ordered {

    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * 브라우저 WebSocket API는 핸드셰이크 요청에 커스텀 헤더를 못 붙이므로, 이 경로 하나만
     * 예외적으로 쿠키에서 토큰을 읽는다. 다른 모든 경로는 절대 쿠키를 보지 않는다 — 쿠키를
     * 일반적인 인증 수단으로 확장하면 헤더 전용이라 꺼둔 CSRF 방어가 무의미해지기 때문.
     */
    private static final String CHAT_WS_HANDSHAKE_PATH = "/api/chat/ws-chat";
    private static final String ACCESS_TOKEN_COOKIE_NAME = "accessToken";

    private final RouteAccessPolicy routeAccessPolicy;
    private final GatewayJwtVerifier jwtVerifier;
    private final ObjectMapper objectMapper;

    public RoleAuthorizationGlobalFilter(
            RouteAccessPolicy routeAccessPolicy,
            GatewayJwtVerifier jwtVerifier,
            ObjectMapper objectMapper
    ) {
        this.routeAccessPolicy = routeAccessPolicy;
        this.jwtVerifier = jwtVerifier;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        AccessRule rule = routeAccessPolicy.resolve(request.getMethod(), request.getPath().value());

        if (rule.type() == AccessType.SKIP || rule.type() == AccessType.PUBLIC) {
            return chain.filter(exchange);
        }

        String token = extractToken(request);
        if (token == null) {
            return reject(exchange, GatewayErrorCode.AUTHENTICATION_REQUIRED);
        }

        Claims claims;
        try {
            claims = jwtVerifier.verify(token);
        } catch (JwtException | IllegalArgumentException e) {
            return reject(exchange, GatewayErrorCode.AUTHENTICATION_REQUIRED);
        }

        if (rule.type() == AccessType.ROLE_RESTRICTED) {
            Role role = parseRole(claims.get("role", String.class));
            if (role == null || !rule.allowedRoles().contains(role)) {
                return reject(exchange, GatewayErrorCode.ACCESS_DENIED);
            }
        }

        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return -1;
    }

    private String extractToken(ServerHttpRequest request) {
        if (CHAT_WS_HANDSHAKE_PATH.equals(request.getPath().value())) {
            return extractCookieToken(request);
        }
        return extractBearerToken(request);
    }

    private String extractBearerToken(ServerHttpRequest request) {
        String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    private String extractCookieToken(ServerHttpRequest request) {
        HttpCookie cookie = request.getCookies().getFirst(ACCESS_TOKEN_COOKIE_NAME);
        return cookie != null ? cookie.getValue() : null;
    }

    private Role parseRole(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Role.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Mono<Void> reject(ServerWebExchange exchange, GatewayErrorCode errorCode) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(errorCode.getHttpStatus());
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = objectMapper.writeValueAsBytes(ApiResponse.error(errorCode));
        DataBuffer buffer = response.bufferFactory().wrap(body);
        return response.writeWith(Mono.just(buffer));
    }
}
