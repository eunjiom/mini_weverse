package com.miniweverse.follow.service;

import com.miniweverse.exception.AuthUserExceptions.DuplicateFollowException;
import com.miniweverse.exception.AuthUserExceptions.InvalidRequestException;
import com.miniweverse.follow.dto.FollowedArtistResponse;
import com.miniweverse.follow.entity.Follow;
import com.miniweverse.follow.repository.FollowRepository;
import com.miniweverse.user.entity.ArtistProfile;
import com.miniweverse.user.entity.User;
import com.miniweverse.user.repository.ArtistProfileRepository;
import com.miniweverse.user.repository.UserRepository;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FollowService {

    private final FollowRepository followRepository;
    private final UserRepository userRepository;
    private final ArtistProfileRepository artistProfileRepository;

    public FollowService(
            FollowRepository followRepository,
            UserRepository userRepository,
            ArtistProfileRepository artistProfileRepository
    ) {
        this.followRepository = followRepository;
        this.userRepository = userRepository;
        this.artistProfileRepository = artistProfileRepository;
    }

    @Transactional
    public void follow(Long followerId, Long artistProfileId) {
        User follower = userRepository.findById(followerId)
                .orElseThrow(() -> new InvalidRequestException("유저 정보를 찾을 수 없습니다."));
        ArtistProfile artist = artistProfileRepository.findById(artistProfileId)
                .orElseThrow(() -> new InvalidRequestException("아티스트 프로필을 찾을 수 없습니다."));
        Follow.create(follower, artist);

        if (followRepository.existsByFollowerAndArtist(followerId, artistProfileId)) {
            throw new DuplicateFollowException();
        }
        try {
            followRepository.insert(followerId, artistProfileId);
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

    /**
     * 팔로우 대상(artist)이 탈퇴했더라도 언팔로우는 항상 가능해야 한다 — follower/artist
     * 존재 여부를 다시 검증하면, 탈퇴한 아티스트를 팔로우 중이던 유저가 영구히 언팔로우할 수
     * 없게 되는 문제가 생긴다. follows row 존재 여부 자체가 이미 신뢰할 수 있는 근거다.
     */
    @Transactional
    public void unfollow(Long followerId, Long artistId) {
        if (!followRepository.existsByFollowerAndArtist(followerId, artistId)) {
            throw new InvalidRequestException("팔로우 중이 아닙니다.");
        }
        followRepository.delete(followerId, artistId);
    }
}
