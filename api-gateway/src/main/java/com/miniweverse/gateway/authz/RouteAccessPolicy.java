package com.miniweverse.gateway.authz;

import com.miniweverse.common.security.jwt.Role;
import java.util.List;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

/**
 * 경로별 접근 규칙. community-service {@code SecurityConfig.apiFilterChain}의
 * authorizeHttpRequests 규칙을 게이트웨이로 이관한 것 — community-service는 더 이상
 * 경로 단위 인가를 하지 않고, 신원 식별(JwtAuthenticationFilter)만 자체적으로 수행한다.
 *
 * {@code /admin/**}은 세션 기반 인증이라 이 필터가 관여하지 않고 그대로 통과시킨다
 * (community-service의 adminFilterChain이 계속 전담).
 */
@Component
public class RouteAccessPolicy {

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    private final List<AccessRule> rules = List.of(
            AccessRule.skip("/admin/**"),
            AccessRule.publicRule("/signup"),
            AccessRule.publicRule("/login"),
            AccessRule.publicRule("/reissue"),
            AccessRule.publicRule("/logout"),
            AccessRule.publicRule("/error"),
            AccessRule.publicRule("/oauth2/**"),
            AccessRule.publicRule("/login/oauth2/**"),
            AccessRule.publicRule(HttpMethod.GET, "/artists"),
            AccessRule.publicRule(HttpMethod.GET, "/artists/*/posts"),
            AccessRule.publicRule(HttpMethod.GET, "/posts/*"),
            AccessRule.publicRule(HttpMethod.GET, "/posts/*/comments"),
            AccessRule.restricted("/api/chat/**", Role.FAN, Role.ARTIST)
    );

    private final AccessRule defaultRule = AccessRule.authenticated("/**");

    public AccessRule resolve(HttpMethod method, String path) {
        return rules.stream()
                .filter(rule -> rule.matches(method, path, pathMatcher))
                .findFirst()
                .orElse(defaultRule);
    }
}
