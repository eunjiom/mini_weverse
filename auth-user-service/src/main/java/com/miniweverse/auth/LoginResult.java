package com.miniweverse.auth;

import com.miniweverse.admin.entity.Admin;
import org.springframework.http.ResponseCookie;

public sealed interface LoginResult {

    record UserLoginResult(String accessToken, ResponseCookie refreshTokenCookie) implements LoginResult {
    }

    record AdminLoginResult(Admin admin) implements LoginResult {
    }
}
