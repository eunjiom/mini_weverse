package com.miniweverse.exception;

import com.miniweverse.common.exception.BusinessException;
import com.miniweverse.exception.code.ChatErrorCode;

/**
 * chat-service의 도메인 예외를 한데 모아둔 컨테이너 (community-service의 AuthUserExceptions와 동일 패턴).
 */
public final class ChatExceptions {

    private ChatExceptions() {
    }

    public static class RoomNotFoundException extends BusinessException {
        public RoomNotFoundException() {
            super(ChatErrorCode.ROOM_NOT_FOUND);
        }
    }

    public static class MembershipRequiredException extends BusinessException {
        public MembershipRequiredException() {
            super(ChatErrorCode.MEMBERSHIP_REQUIRED);
        }
    }

    public static class InvalidRequestException extends BusinessException {
        public InvalidRequestException(String message) {
            super(ChatErrorCode.INVALID_REQUEST, message);
        }
    }
}
