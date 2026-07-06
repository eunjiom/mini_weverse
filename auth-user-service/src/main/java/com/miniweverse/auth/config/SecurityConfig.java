package com.miniweverse.auth.config;

import com.miniweverse.auth.jwt.JwtAuthenticationEntryPoint;
import com.miniweverse.auth.jwt.JwtAuthenticationFilter;
import com.miniweverse.auth.jwt.JwtTokenProvider;
import com.miniweverse.auth.oauth.KakaoLoginSuccessHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

/**
 * /admin/** 는 세션 기반(1번), 그 외 전부는 JWT 기반(2번)으로 필터 체인을 분리한다.
 * oauth2Login()이 리다이렉트 흐름 중 임시 상태를 세션에 저장해야 해서, apiFilterChain은
 * STATELESS 대신 IF_REQUIRED로 둔다 (우리 자체 인증은 JwtAuthenticationFilter가 세션과 무관하게 처리).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final KakaoLoginSuccessHandler kakaoLoginSuccessHandler;

    public SecurityConfig(
            JwtTokenProvider jwtTokenProvider,
            JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint,
            KakaoLoginSuccessHandler kakaoLoginSuccessHandler
    ) {
        this.jwtAuthenticationFilter = new JwtAuthenticationFilter(jwtTokenProvider);
        this.jwtAuthenticationEntryPoint = jwtAuthenticationEntryPoint;
        this.kakaoLoginSuccessHandler = kakaoLoginSuccessHandler;
    }

    @Bean
    @Order(1)
    public SecurityFilterChain adminFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/admin/**")
                // 세션(쿠키) 기반 인증이라 CSRF에 노출될 수 있어, JWT 체인과 달리 여기는 CSRF를 켠다.
                .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
                // 최신 Spring Security 기본 SecurityContextRepository는 세션에서 자동으로 복원해주지 않아서 명시한다.
                // AuthController.establishAdminSession()이 로그인 시 이 저장소로 세션에 SecurityContext를 저장해둔다.
                .securityContext(securityContext ->
                        securityContext.securityContextRepository(new HttpSessionSecurityContextRepository()))
                .authorizeHttpRequests(auth -> auth.anyRequest().hasRole("ADMIN"));
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/signup", "/login", "/reissue", "/logout", "/error", "/oauth2/**", "/login/oauth2/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(jwtAuthenticationEntryPoint))
                .oauth2Login(oauth2 -> oauth2.successHandler(kakaoLoginSuccessHandler))
                // 기본 LogoutFilter가 POST /logout을 가로채 리다이렉트시키는 걸 막고, AuthController.logout()만 쓴다.
                .logout(AbstractHttpConfigurer::disable)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
