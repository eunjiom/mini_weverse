package com.miniweverse.user.service;

import com.miniweverse.auth.repository.RefreshTokenRepository;
import com.miniweverse.exception.AuthUserExceptions.InvalidCredentialsException;
import com.miniweverse.exception.AuthUserExceptions.InvalidRequestException;
import com.miniweverse.follow.repository.FollowRepository;
import com.miniweverse.user.dto.UserProfileResponse;
import com.miniweverse.user.entity.User;
import com.miniweverse.user.enums.AuthProvider;
import com.miniweverse.user.repository.UserRepository;
import java.util.Objects;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenRepository refreshTokenRepository;
    private final FollowRepository followRepository;

    public UserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            RefreshTokenRepository refreshTokenRepository,
            FollowRepository followRepository
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenRepository = refreshTokenRepository;
        this.followRepository = followRepository;
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(Long viewerId, Long targetUserId) {
        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new InvalidRequestException("유저 정보를 찾을 수 없습니다."));

        boolean isSelf = Objects.equals(viewerId, targetUserId);
        return new UserProfileResponse(
                targetUser.getId(),
                targetUser.getNickname(),
                followRepository.countFollowers(targetUserId),
                followRepository.countFollowing(targetUserId),
                isSelf ? followRepository.findFollowedArtists(targetUserId) : null
        );
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
