package com.miniweverse.gateway.exception;

import com.miniweverse.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum GatewayErrorCode implements ErrorCode {

    AUTHENTICATION_REQUIRED("GATEWAY_001", "인증이 필요합니다.", HttpStatus.UNAUTHORIZED),
    ACCESS_DENIED("GATEWAY_002", "접근 권한이 없습니다.", HttpStatus.FORBIDDEN);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    GatewayErrorCode(String code, String message, HttpStatus httpStatus) {
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
