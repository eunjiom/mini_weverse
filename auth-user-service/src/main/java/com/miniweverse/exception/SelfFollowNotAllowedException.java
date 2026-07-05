package com.miniweverse.exception;

import com.miniweverse.common.exception.BusinessException;

public class SelfFollowNotAllowedException extends BusinessException {

    public SelfFollowNotAllowedException() {
        super(AuthUserErrorCode.SELF_FOLLOW_NOT_ALLOWED);
    }
}
