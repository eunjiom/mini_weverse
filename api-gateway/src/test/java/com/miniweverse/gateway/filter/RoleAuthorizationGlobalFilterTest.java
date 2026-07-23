package com.miniweverse.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.miniweverse.common.security.jwt.JwtVerifier;
import com.miniweverse.gateway.authz.AccessRule;
import com.miniweverse.gateway.authz.RouteAccessPolicy;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class RoleAuthorizationGlobalFilterTest {

    @Mock
    private RouteAccessPolicy routeAccessPolicy;
    @Mock
    private JwtVerifier jwtVerifier;

    private RoleAuthorizationGlobalFilter filter;
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new RoleAuthorizationGlobalFilter(routeAccessPolicy, jwtVerifier, new ObjectMapper());
        chain = mock(GatewayFilterChain.class);
    }

    @Test
    void PUBLIC_규칙이면_토큰_검증_없이_체인을_그대로_통과시킨다() {
        ServerWebExchange exchange = exchangeFor(HttpMethod.GET, "/artists");
        given(routeAccessPolicy.resolve(HttpMethod.GET, "/artists")).willReturn(AccessRule.publicRule(HttpMethod.GET, "/artists"));
        given(chain.filter(exchange)).willReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain, times(1)).filter(exchange);
        verify(jwtVerifier, never()).parseClaims(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void AUTHENTICATED_규칙인데_토큰이_없으면_401로_거부한다() {
        ServerWebExchange exchange = exchangeFor(HttpMethod.GET, "/posts");
        given(routeAccessPolicy.resolve(HttpMethod.GET, "/posts")).willReturn(AccessRule.authenticated("/posts"));

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(exchange);
    }

    @Test
    void AUTHENTICATED_규칙에서_유효한_토큰이면_체인을_통과시킨다() {
        ServerWebExchange exchange = exchangeFor(HttpMethod.GET, "/posts", "valid-token");
        given(routeAccessPolicy.resolve(HttpMethod.GET, "/posts")).willReturn(AccessRule.authenticated("/posts"));
        given(jwtVerifier.parseClaims("valid-token")).willReturn(claimsWithRole("FAN"));
        given(chain.filter(exchange)).willReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain, times(1)).filter(exchange);
        verify(jwtVerifier, times(1)).parseClaims("valid-token");
    }

    @Test
    void ROLE_RESTRICTED_규칙에서_허용된_role이면_체인을_통과시킨다() {
        ServerWebExchange exchange = exchangeFor(HttpMethod.GET, "/api/chat/rooms", "valid-token");
        given(routeAccessPolicy.resolve(HttpMethod.GET, "/api/chat/rooms"))
                .willReturn(AccessRule.restricted("/api/chat/**", com.miniweverse.common.security.jwt.Role.FAN, com.miniweverse.common.security.jwt.Role.ARTIST));
        given(jwtVerifier.parseClaims("valid-token")).willReturn(claimsWithRole("FAN"));
        given(chain.filter(exchange)).willReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain, times(1)).filter(exchange);
    }

    @Test
    void ROLE_RESTRICTED_규칙에서_허용되지_않은_role이면_403으로_거부한다() {
        ServerWebExchange exchange = exchangeFor(HttpMethod.GET, "/api/chat/rooms", "valid-token");
        given(routeAccessPolicy.resolve(HttpMethod.GET, "/api/chat/rooms"))
                .willReturn(AccessRule.restricted("/api/chat/**", com.miniweverse.common.security.jwt.Role.FAN, com.miniweverse.common.security.jwt.Role.ARTIST));
        given(jwtVerifier.parseClaims("valid-token")).willReturn(claimsWithRole("ADMIN"));

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(chain, never()).filter(exchange);
    }

    @Test
    void 위조된_토큰이면_401로_거부한다() {
        ServerWebExchange exchange = exchangeFor(HttpMethod.GET, "/posts", "forged-token");
        given(routeAccessPolicy.resolve(HttpMethod.GET, "/posts")).willReturn(AccessRule.authenticated("/posts"));
        given(jwtVerifier.parseClaims("forged-token")).willThrow(new SignatureException("invalid signature"));

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private Claims claimsWithRole(String role) {
        return Jwts.claims().subject("1").add("role", role).build();
    }

    private ServerWebExchange exchangeFor(HttpMethod method, String path) {
        return MockServerWebExchange.from(MockServerHttpRequest.method(method, path).build());
    }

    private ServerWebExchange exchangeFor(HttpMethod method, String path, String bearerToken) {
        return MockServerWebExchange.from(
                MockServerHttpRequest.method(method, path)
                        .header("Authorization", "Bearer " + bearerToken)
                        .build()
        );
    }
}
