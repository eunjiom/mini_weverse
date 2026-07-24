package com.miniweverse.common.exception;

import org.springframework.http.HttpStatus;

/**
 * JWT 인증 실패(인증 필요/토큰 만료/유효하지 않은 토큰) 3가지는 모든 서비스가 메시지·상태코드까지
 * 완전히 동일하게 각자 enum으로 중복 정의하고 있었다. 에러 코드 문자열의 접두사(prefix)만 서비스마다
 * 달라서 enum 상수 하나로 통일할 수 없어, 코드 문자열을 서비스가 직접 넘기는 정적 팩토리로 공통화한다.
 */
public final class JwtAuthErrorCode implements ErrorCode {

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    private JwtAuthErrorCode(String code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public static JwtAuthErrorCode authenticationRequired(String code) {
        return new JwtAuthErrorCode(code, "인증이 필요합니다.", HttpStatus.UNAUTHORIZED);
    }

    public static JwtAuthErrorCode tokenExpired(String code) {
        return new JwtAuthErrorCode(code, "액세스 토큰이 만료되었습니다.", HttpStatus.UNAUTHORIZED);
    }

    public static JwtAuthErrorCode invalidToken(String code) {
        return new JwtAuthErrorCode(code, "유효하지 않은 토큰입니다.", HttpStatus.UNAUTHORIZED);
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
