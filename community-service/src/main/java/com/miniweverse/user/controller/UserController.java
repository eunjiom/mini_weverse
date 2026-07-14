package com.miniweverse.user.controller;

import com.miniweverse.auth.service.AuthService;
import com.miniweverse.user.dto.UserProfileResponse;
import com.miniweverse.user.dto.WithdrawRequest;
import com.miniweverse.user.service.UserService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserController {

    private final UserService userService;
    private final AuthService authService;

    public UserController(UserService userService, AuthService authService) {
        this.userService = userService;
        this.authService = authService;
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<UserProfileResponse> getProfile(
            @AuthenticationPrincipal Long viewerId,
            @PathVariable Long userId
    ) {
        return ResponseEntity.ok(userService.getProfile(viewerId, userId));
    }

    @DeleteMapping("/users/me")
    public ResponseEntity<Void> withdraw(
            @AuthenticationPrincipal Long userId,
            @RequestBody(required = false) WithdrawRequest request
    ) {
        String password = request != null ? request.password() : null;
        userService.withdraw(userId, password);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, authService.expiredRefreshTokenCookie().toString())
                .build();
    }
}
