package com.miniweverse.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 각 서비스는 이 인터페이스를 구현하는 enum으로 자기 도메인의 에러 코드를 정의한다.
 */
public interface ErrorCode {

    String getCode();

    String getMessage();

    HttpStatus getHttpStatus();
}
