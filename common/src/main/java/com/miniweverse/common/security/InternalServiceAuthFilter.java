package com.miniweverse.common.security;

import com.miniweverse.common.exception.CommonErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * {@code /internal/**}는 게이트웨이를 거치지 않는 서비스 간 전용 API라 유저 JWT가 없다. 대신 내부
 * 서비스끼리 공유하는 고정 시크릿을 헤더로 검증해서, 그 포트에 접근 가능한 아무나가 멤버십 상태를
 * 조작하지 못하게 막는다. 각 서비스 SecurityConfig가 자기 설정값으로 직접 생성해서 등록한다
 * (JwtAuthenticationFilter와 동일한 패턴 — @Component로 두지 않는다).
 *
 * 호출자는 전부 서비스 자체 RestClient(내부 호출 실패 시 그냥 예외로 처리)라 응답 바디를 파싱하지
 * 않으므로, 상태 코드만 내려주고 바디는 비워둔다(공통 모듈에 Jackson 의존성을 새로 추가하지 않기 위함).
 */
public class InternalServiceAuthFilter extends OncePerRequestFilter {

    public static final String SECRET_HEADER_NAME = "X-Internal-Secret";

    private static final String INTERNAL_PATH_PREFIX = "/internal/";
    private static final Logger log = LoggerFactory.getLogger(InternalServiceAuthFilter.class);

    private final String expectedSecret;

    public InternalServiceAuthFilter(String expectedSecret) {
        // 시크릿이 빈 값이면 MessageDigest.isEqual("", "")가 true를 반환해서 인증이 무력화되므로,
        // 그런 설정으로는 아예 기동되지 않게 막는다.
        Assert.hasText(expectedSecret, "internal.service-secret must not be blank");
        this.expectedSecret = expectedSecret;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (!request.getRequestURI().startsWith(INTERNAL_PATH_PREFIX) || matchesSecret(request.getHeader(SECRET_HEADER_NAME))) {
            filterChain.doFilter(request, response);
            return;
        }
        log.warn("Rejected internal API call with invalid secret: {} {} from {}",
                request.getMethod(), request.getRequestURI(), request.getRemoteAddr());
        response.setStatus(CommonErrorCode.UNAUTHORIZED.getHttpStatus().value());
    }

    private boolean matchesSecret(String secret) {
        if (secret == null) {
            return false;
        }
        return MessageDigest.isEqual(
                secret.getBytes(StandardCharsets.UTF_8),
                expectedSecret.getBytes(StandardCharsets.UTF_8));
    }
}
