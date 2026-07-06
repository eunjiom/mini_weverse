package com.miniweverse.auth.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authorization: Bearer {AT} 헤더를 검증해서 SecurityContext에 인증 정보를 채운다.
 * 토큰이 없거나 유효하지 않으면 그냥 인증 없이 통과시키고, 이후 authorizeHttpRequests가 401/403을 판단한다.
 *
 * AT는 순수 stateless로 검증한다 (Redis 조회 없음). 로그아웃/탈취 감지는 RT(재발급)만 즉시 차단하고,
 * 이미 발급된 AT는 자체 만료 시간(짧게 유지)까지만 유효하다 — 매 요청마다 Redis를 보면 세션과
 * 다를 게 없어져서 JWT를 쓰는 의미(무상태, 서버 확장 용이)가 사라지기 때문에 이렇게 유지한다.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());
            try {
                Claims claims = jwtTokenProvider.parseClaims(token);
                if (!JwtTokenProvider.TOKEN_TYPE_ACCESS.equals(claims.get(JwtTokenProvider.CLAIM_TOKEN_TYPE, String.class))) {
                    SecurityContextHolder.clearContext();
                } else {
                    Long userId = Long.valueOf(claims.getSubject());
                    String role = claims.get("role", String.class);
                    List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(userId, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (JwtException | IllegalArgumentException e) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
