package com.miniweverse.auth.jwt;

import com.miniweverse.exception.code.ChatErrorCode;
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
 * community-service의 JwtAuthenticationFilter와 동일한 패턴 — 게이트웨이를 신뢰하지 않고
 * 이 서비스도 독립적으로 서명을 검증한다.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String TOKEN_ERROR_ATTRIBUTE = "tokenError";

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
                    request.setAttribute(TOKEN_ERROR_ATTRIBUTE, ChatErrorCode.INVALID_TOKEN);
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
                request.setAttribute(TOKEN_ERROR_ATTRIBUTE, ChatErrorCode.TOKEN_EXPIRED);
                SecurityContextHolder.clearContext();
            } catch (JwtException | IllegalArgumentException e) {
                request.setAttribute(TOKEN_ERROR_ATTRIBUTE, ChatErrorCode.INVALID_TOKEN);
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
