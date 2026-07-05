package com.miniweverse.exception;

import com.miniweverse.common.exception.BusinessException;

public class NotArtistException extends BusinessException {

    public NotArtistException() {
        super(AuthUserErrorCode.TARGET_NOT_ARTIST);
    }
}
