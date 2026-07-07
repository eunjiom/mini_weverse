package com.miniweverse.auth.service;

import com.miniweverse.user.entity.User;
import org.springframework.http.ResponseCookie;

public sealed interface LoginResult {

    record UserLoginResult(String accessToken, ResponseCookie refreshTokenCookie) implements LoginResult {
    }

    record AdminLoginResult(User user) implements LoginResult {
    }
}
