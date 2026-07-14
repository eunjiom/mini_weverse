package com.miniweverse.artist.service;

import com.miniweverse.artist.dto.ArtistProfileDetailResponse;
import com.miniweverse.artist.dto.ArtistSearchResponse;
import com.miniweverse.common.response.CursorPageResponse;
import com.miniweverse.exception.AuthUserExceptions.InvalidRequestException;
import com.miniweverse.exception.AuthUserExceptions.NotFollowingArtistException;
import com.miniweverse.follow.repository.FollowRepository;
import com.miniweverse.user.entity.ArtistProfile;
import com.miniweverse.user.entity.User;
import com.miniweverse.user.repository.ArtistProfileRepository;
import java.util.List;
import java.util.Objects;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ArtistService {

    private final ArtistProfileRepository artistProfileRepository;
    private final FollowRepository followRepository;

    public ArtistService(ArtistProfileRepository artistProfileRepository, FollowRepository followRepository) {
        this.artistProfileRepository = artistProfileRepository;
        this.followRepository = followRepository;
    }

    @Transactional(readOnly = true)
    public CursorPageResponse<ArtistSearchResponse> search(String name, Long cursor, int size) {
        List<ArtistSearchResponse> fetched = artistProfileRepository
                .findByChannelNameAndCursor(name, cursor, PageRequest.of(0, size + 1))
                .stream()
                .map(ArtistSearchResponse::from)
                .toList();
        return CursorPageResponse.of(fetched, size, ArtistSearchResponse::artistId);
    }

    /**
     * 게시글 열람과 달리 프로필 조회는 팔로우한 사람만 볼 수 있다(본인은 예외) — 튜터 피드백을 그대로 반영한 정책.
     */
    @Transactional(readOnly = true)
    public ArtistProfileDetailResponse getProfile(Long viewerId, Long artistProfileId) {
        ArtistProfile artistProfile = artistProfileRepository.findById(artistProfileId)
                .orElseThrow(() -> new InvalidRequestException("아티스트 프로필을 찾을 수 없습니다."));

        User artistUser = artistProfile.getUser();
        boolean isArtistSelf = artistUser != null && Objects.equals(viewerId, artistUser.getId());
        if (!isArtistSelf && !followRepository.existsByFollowerAndArtist(viewerId, artistProfileId)) {
            throw new NotFollowingArtistException();
        }

        return ArtistProfileDetailResponse.from(artistProfile);
    }
}
