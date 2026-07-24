package com.miniweverse.auth.jwt;

import com.miniweverse.common.exception.JwtAuthErrorCode;
import com.miniweverse.common.security.jwt.JwtVerifier;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
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
 * 실패 사유(만료/위조 등)는 request attribute에 담아 {@link JwtAuthenticationEntryPoint}가 응답 바디에 반영한다
 * (프론트가 "만료라 재발급하면 되는지" "위조라 재로그인해야 하는지" 구분할 수 있게).
 *
 * AT는 순수 stateless로 검증한다 (Redis 조회 없음). 로그아웃/탈취 감지는 RT(재발급)만 즉시 차단하고,
 * 이미 발급된 AT는 자체 만료 시간(짧게 유지)까지만 유효하다 — 매 요청마다 Redis를 보면 세션과
 * 다를 게 없어져서 JWT를 쓰는 의미(무상태, 서버 확장 용이)가 사라지기 때문에 이렇게 유지한다.
 * 같은 이유로 탈퇴(soft delete)한 유저의 AT도 만료 전까지는 서명/기간만으로 통과될 수 있다 — 실제
 * 유저 데이터를 다시 조회하는 비즈니스 로직은 {@code deleted_at} 필터로 걸러지므로 감수 가능한 범위로 본다.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String TOKEN_ERROR_ATTRIBUTE = "tokenError";

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtVerifier jwtVerifier;

    public JwtAuthenticationFilter(JwtVerifier jwtVerifier) {
        this.jwtVerifier = jwtVerifier;
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
                Claims claims = jwtVerifier.parseClaims(token);
                if (!JwtVerifier.TOKEN_TYPE_ACCESS.equals(claims.get(JwtVerifier.CLAIM_TOKEN_TYPE, String.class))) {
                    request.setAttribute(TOKEN_ERROR_ATTRIBUTE, JwtAuthErrorCode.invalidToken("AUTH_USER_010"));
                    SecurityContextHolder.clearContext();
                } else {
                    Long userId = Long.valueOf(claims.getSubject());
                    String role = claims.get("role", String.class);
                    List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(userId, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (ExpiredJwtException e) {
                request.setAttribute(TOKEN_ERROR_ATTRIBUTE, JwtAuthErrorCode.tokenExpired("AUTH_USER_009"));
                SecurityContextHolder.clearContext();
            } catch (JwtException | IllegalArgumentException e) {
                request.setAttribute(TOKEN_ERROR_ATTRIBUTE, JwtAuthErrorCode.invalidToken("AUTH_USER_010"));
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
