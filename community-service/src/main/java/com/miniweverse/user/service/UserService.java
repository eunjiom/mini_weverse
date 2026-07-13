package com.miniweverse.user.service;

import com.miniweverse.auth.repository.RefreshTokenRepository;
import com.miniweverse.exception.AuthUserExceptions.InvalidCredentialsException;
import com.miniweverse.exception.AuthUserExceptions.InvalidRequestException;
import com.miniweverse.user.entity.User;
import com.miniweverse.user.enums.AuthProvider;
import com.miniweverse.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenRepository refreshTokenRepository;

    public UserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            RefreshTokenRepository refreshTokenRepository
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Transactional
    public void withdraw(Long userId, String rawPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidRequestException("유저 정보를 찾을 수 없습니다."));

        if (user.getProvider() == AuthProvider.LOCAL) {
            validatePassword(rawPassword, user.getPassword());
        }

        user.delete();
        refreshTokenRepository.delete(userId);
    }

    private void validatePassword(String rawPassword, String encodedPassword) {
        if (encodedPassword == null || rawPassword == null || !passwordEncoder.matches(rawPassword, encodedPassword)) {
            throw new InvalidCredentialsException();
        }
    }
}
