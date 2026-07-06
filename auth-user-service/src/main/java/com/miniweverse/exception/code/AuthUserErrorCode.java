package com.miniweverse.exception.code;

import com.miniweverse.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum AuthUserErrorCode implements ErrorCode {

    DUPLICATE_FOLLOW("AUTH_USER_001", "이미 팔로우한 아티스트입니다.", HttpStatus.CONFLICT),
    SELF_FOLLOW_NOT_ALLOWED("AUTH_USER_002", "자기 자신을 팔로우할 수 없습니다.", HttpStatus.BAD_REQUEST),
    INVALID_ARTIST_PROFILE("AUTH_USER_003", "유효하지 않은 아티스트 프로필입니다.", HttpStatus.BAD_REQUEST),
    TARGET_NOT_ARTIST("AUTH_USER_004", "대상 유저가 ARTIST 권한이 아닙니다.", HttpStatus.BAD_REQUEST),
    INVALID_REQUEST("AUTH_USER_005", "요청 값이 올바르지 않습니다.", HttpStatus.BAD_REQUEST),
    DUPLICATE_EMAIL("AUTH_USER_006", "이미 가입된 이메일입니다.", HttpStatus.CONFLICT),
    INVALID_CREDENTIALS("AUTH_USER_007", "이메일 또는 비밀번호가 올바르지 않습니다.", HttpStatus.UNAUTHORIZED);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    AuthUserErrorCode(String code, String message, HttpStatus httpStatus) {
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
