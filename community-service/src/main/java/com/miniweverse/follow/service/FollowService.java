package com.miniweverse.follow.service;

import com.miniweverse.exception.AuthUserExceptions.DuplicateFollowException;
import com.miniweverse.exception.AuthUserExceptions.InvalidRequestException;
import com.miniweverse.follow.dto.FollowedArtistResponse;
import com.miniweverse.follow.entity.Follow;
import com.miniweverse.follow.repository.FollowRepository;
import com.miniweverse.user.entity.User;
import com.miniweverse.user.repository.UserRepository;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FollowService {

    private final FollowRepository followRepository;
    private final UserRepository userRepository;

    public FollowService(FollowRepository followRepository, UserRepository userRepository) {
        this.followRepository = followRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void follow(Long followerId, Long artistId) {
        User follower = userRepository.findById(followerId)
                .orElseThrow(() -> new InvalidRequestException("유저 정보를 찾을 수 없습니다."));
        User artist = userRepository.findById(artistId)
                .orElseThrow(() -> new InvalidRequestException("아티스트 정보를 찾을 수 없습니다."));
        Follow.create(follower, artist);

        if (followRepository.existsByFollowerAndArtist(followerId, artistId)) {
            throw new DuplicateFollowException();
        }
        try {
            followRepository.insert(followerId, artistId);
        } catch (DataIntegrityViolationException e) {
            // 동시 요청으로 중복 확인을 통과한 뒤 유니크 제약에서 걸린 경우.
            throw new DuplicateFollowException();
        }
    }

    public List<FollowedArtistResponse> getFollowedArtists(Long followerId) {
        userRepository.findById(followerId)
                .orElseThrow(() -> new InvalidRequestException("유저 정보를 찾을 수 없습니다."));
        return followRepository.findFollowedArtists(followerId);
    }

    @Transactional
    public void unfollow(Long followerId, Long artistId) {
        userRepository.findById(followerId)
                .orElseThrow(() -> new InvalidRequestException("유저 정보를 찾을 수 없습니다."));
        userRepository.findById(artistId)
                .orElseThrow(() -> new InvalidRequestException("아티스트 정보를 찾을 수 없습니다."));

        if (!followRepository.existsByFollowerAndArtist(followerId, artistId)) {
            throw new InvalidRequestException("팔로우 중이 아닙니다.");
        }
        followRepository.delete(followerId, artistId);
    }
}
