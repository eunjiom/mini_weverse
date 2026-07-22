package com.miniweverse.auth.config;

import com.miniweverse.auth.jwt.JwtAuthenticationEntryPoint;
import com.miniweverse.auth.jwt.JwtAuthenticationFilter;
import com.miniweverse.common.security.InternalServiceAuthFilter;
import com.miniweverse.common.security.jwt.JwtVerifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * chat-service는 게이트웨이를 신뢰하지 않고 독자적으로 JWT 서명을 검증한다(defense in depth).
 * 경로별 세부 인가(공개/비공개)는 게이트웨이의 RouteAccessPolicy가 이미 담당하므로, 여기서는
 * "로그인 여부"만 확인한다 — 관리자 세션 체인 같은 별도 분기가 없어 community-service보다 단순.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final InternalServiceAuthFilter internalServiceAuthFilter;

    public SecurityConfig(
            JwtVerifier jwtVerifier,
            JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint,
            @Value("${internal.service-secret}") String internalServiceSecret
    ) {
        this.jwtAuthenticationFilter = new JwtAuthenticationFilter(jwtVerifier);
        this.jwtAuthenticationEntryPoint = jwtAuthenticationEntryPoint;
        this.internalServiceAuthFilter = new InternalServiceAuthFilter(internalServiceSecret);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/error").permitAll()
                        // WebSocket 핸드셰이크는 이 서블릿 필터(Authorization 헤더 기반)가 아니라
                        // JwtHandshakeInterceptor(쿠키 기반)가 별도로 인증한다.
                        .requestMatchers("/api/chat/ws-chat").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        // 다른 내부 서비스(community-service 등)가 게이트웨이를 거치지 않고 직접
                        // 호출하는 서비스 간 전용 API. 유저 JWT가 없는 호출이라 내부망 신뢰 전제로 permitAll.
                        .requestMatchers("/internal/**").permitAll()
                        // Prometheus가 유저 JWT 없이 스크레이프한다 — health/prometheus 2개만 노출 중(management.endpoints.web.exposure.include).
                        .requestMatchers("/actuator/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(jwtAuthenticationEntryPoint))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(internalServiceAuthFilter, JwtAuthenticationFilter.class);
        return http.build();
    }
}
