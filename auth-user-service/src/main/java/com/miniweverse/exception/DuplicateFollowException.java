package com.miniweverse.exception;

import com.miniweverse.common.exception.BusinessException;

public class DuplicateFollowException extends BusinessException {

    public DuplicateFollowException() {
        super(AuthUserErrorCode.DUPLICATE_FOLLOW);
    }
}
