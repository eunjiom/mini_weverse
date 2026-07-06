package com.miniweverse.exception;

import com.miniweverse.common.exception.BusinessException;
import com.miniweverse.exception.code.AuthUserErrorCode;

/**
 * auth-user-service의 도메인 예외를 한데 모아둔 컨테이너.
 * 사용하는 쪽에서는 중첩 클래스를 개별 import해서 예전처럼 {@code new DuplicateFollowException()}으로 사용한다.
 */
public final class AuthUserExceptions {

    private AuthUserExceptions() {
    }

    public static class DuplicateFollowException extends BusinessException {
        public DuplicateFollowException() {
            super(AuthUserErrorCode.DUPLICATE_FOLLOW);
        }
    }

    public static class SelfFollowNotAllowedException extends BusinessException {
        public SelfFollowNotAllowedException() {
            super(AuthUserErrorCode.SELF_FOLLOW_NOT_ALLOWED);
        }
    }

    public static class InvalidArtistProfileException extends BusinessException {
        public InvalidArtistProfileException(String message) {
            super(AuthUserErrorCode.INVALID_ARTIST_PROFILE, message);
        }
    }

    public static class NotArtistException extends BusinessException {
        public NotArtistException() {
            super(AuthUserErrorCode.TARGET_NOT_ARTIST);
        }
    }

    public static class InvalidRequestException extends BusinessException {
        public InvalidRequestException(String message) {
            super(AuthUserErrorCode.INVALID_REQUEST, message);
        }
    }

    public static class DuplicateEmailException extends BusinessException {
        public DuplicateEmailException() {
            super(AuthUserErrorCode.DUPLICATE_EMAIL);
        }
    }

    public static class InvalidCredentialsException extends BusinessException {
        public InvalidCredentialsException() {
            super(AuthUserErrorCode.INVALID_CREDENTIALS);
        }
    }

    public static class InvalidRefreshTokenException extends BusinessException {
        public InvalidRefreshTokenException() {
            super(AuthUserErrorCode.INVALID_REFRESH_TOKEN);
        }
    }
}
