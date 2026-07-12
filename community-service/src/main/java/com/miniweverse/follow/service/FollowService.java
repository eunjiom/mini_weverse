package com.miniweverse.follow.service;

import com.miniweverse.exception.AuthUserExceptions.DuplicateFollowException;
import com.miniweverse.exception.AuthUserExceptions.InvalidRequestException;
import com.miniweverse.follow.entity.Follow;
import com.miniweverse.follow.repository.FollowRepository;
import com.miniweverse.user.entity.User;
import com.miniweverse.user.repository.UserRepository;
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
    public Follow follow(Long followerId, Long artistId) {
        User follower = userRepository.findById(followerId)
                .orElseThrow(() -> new InvalidRequestException("유저 정보를 찾을 수 없습니다."));
        User artist = userRepository.findById(artistId)
                .orElseThrow(() -> new InvalidRequestException("아티스트 정보를 찾을 수 없습니다."));

        if (followRepository.findByFollowerAndArtist(follower, artist).isPresent()) {
            throw new DuplicateFollowException();
        }
        try {
            return followRepository.save(Follow.create(follower, artist));
        } catch (DataIntegrityViolationException e) {
            // 동시 요청으로 중복 확인을 통과한 뒤 유니크 제약에서 걸린 경우.
            throw new DuplicateFollowException();
        }
    }

    @Transactional
    public void unfollow(Long followerId, Long artistId) {
        User follower = userRepository.findById(followerId)
                .orElseThrow(() -> new InvalidRequestException("유저 정보를 찾을 수 없습니다."));
        User artist = userRepository.findById(artistId)
                .orElseThrow(() -> new InvalidRequestException("아티스트 정보를 찾을 수 없습니다."));

        Follow follow = followRepository.findByFollowerAndArtist(follower, artist)
                .orElseThrow(() -> new InvalidRequestException("팔로우 중이 아닙니다."));
        followRepository.delete(follow);
    }
}
