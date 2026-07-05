package com.miniweverse.exception;

import com.miniweverse.common.exception.BusinessException;

public class InvalidRequestException extends BusinessException {

    public InvalidRequestException(String message) {
        super(AuthUserErrorCode.INVALID_REQUEST, message);
    }
}
