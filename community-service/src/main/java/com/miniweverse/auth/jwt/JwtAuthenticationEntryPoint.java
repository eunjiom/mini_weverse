package com.miniweverse.auth.jwt;

import com.miniweverse.common.exception.JwtAuthErrorCode;
import com.miniweverse.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * JwtAuthenticationFilter가 request attribute에 남겨둔 실패 사유(만료/위조 등)를 읽어서,
 * 다른 API 응답과 동일한 형식({@link ApiResponse})으로 401 바디를 내려준다.
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public JwtAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {
        Object attribute = request.getAttribute(JwtAuthenticationFilter.TOKEN_ERROR_ATTRIBUTE);
        JwtAuthErrorCode errorCode = attribute instanceof JwtAuthErrorCode code
                ? code
                : JwtAuthErrorCode.authenticationRequired("AUTH_USER_011");

        response.setStatus(errorCode.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.error(errorCode)));
    }
}
