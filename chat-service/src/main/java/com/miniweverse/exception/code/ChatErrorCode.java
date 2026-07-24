package com.miniweverse.exception.code;

import com.miniweverse.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum ChatErrorCode implements ErrorCode {

    ROOM_NOT_FOUND("CHAT_004", "존재하지 않는 채팅방입니다.", HttpStatus.NOT_FOUND),
    MEMBERSHIP_REQUIRED("CHAT_005", "멤버십 구독자만 입장할 수 있습니다.", HttpStatus.FORBIDDEN),
    INVALID_REQUEST("CHAT_006", "요청 값이 올바르지 않습니다.", HttpStatus.BAD_REQUEST);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    ChatErrorCode(String code, String message, HttpStatus httpStatus) {
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
