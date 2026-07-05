package com.miniweverse.common.exception;

import lombok.Getter;

/**
 * 각 서비스의 도메인 예외는 이 클래스를 상속해서 정의한다.
 * {@link ErrorCode}를 생성자에서 받아 보관하고, {@code @RestControllerAdvice}가 이를 통해 응답을 만든다.
 */
@Getter
public abstract class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    protected BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    protected BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
