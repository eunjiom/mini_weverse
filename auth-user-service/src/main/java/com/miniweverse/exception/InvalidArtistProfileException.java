package com.miniweverse.exception;

import com.miniweverse.common.exception.BusinessException;

public class InvalidArtistProfileException extends BusinessException {

    public InvalidArtistProfileException(String message) {
        super(AuthUserErrorCode.INVALID_ARTIST_PROFILE, message);
    }
}
