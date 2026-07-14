package com.miniweverse.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 특정 도메인에 속하지 않는, 서비스 공통으로 발생할 수 있는 에러코드.
 */
public enum CommonErrorCode implements ErrorCode {

    VALIDATION_ERROR("COMMON_001", "요청 값이 올바르지 않습니다.", HttpStatus.BAD_REQUEST),
    INTERNAL_SERVER_ERROR("COMMON_002", "서버 내부 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    CommonErrorCode(String code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
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
