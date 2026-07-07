package com.miniweverse.membership.service;

import com.miniweverse.exception.AuthUserExceptions.InvalidRequestException;
import com.miniweverse.user.entity.ArtistProfile;
import com.miniweverse.user.entity.MembershipCache;
import com.miniweverse.user.entity.User;
import com.miniweverse.user.enums.ArtistCategory;
import com.miniweverse.user.enums.MembershipStatus;
import com.miniweverse.user.repository.ArtistProfileRepository;
import com.miniweverse.user.repository.MembershipCacheRepository;
import com.miniweverse.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MembershipService {

    private final MembershipCacheRepository membershipCacheRepository;
    private final UserRepository userRepository;
    private final ArtistProfileRepository artistProfileRepository;

    public MembershipService(
            MembershipCacheRepository membershipCacheRepository,
            UserRepository userRepository,
            ArtistProfileRepository artistProfileRepository
    ) {
        this.membershipCacheRepository = membershipCacheRepository;
        this.userRepository = userRepository;
        this.artistProfileRepository = artistProfileRepository;
    }

    @Transactional
    public MembershipCache subscribe(Long subscriberId, Long artistId) {
        User subscriber = userRepository.findById(subscriberId)
                .orElseThrow(() -> new InvalidRequestException("구독자 정보를 찾을 수 없습니다."));
        User artist = userRepository.findById(artistId)
                .orElseThrow(() -> new InvalidRequestException("아티스트 정보를 찾을 수 없습니다."));

        ArtistProfile artistProfile = artistProfileRepository.findByUser(artist)
                .orElseThrow(() -> new InvalidRequestException("아티스트 프로필을 찾을 수 없습니다."));
        if (artistProfile.getCategory() == ArtistCategory.MEMBER) {
            throw new InvalidRequestException("그룹 멤버는 구독할 수 없습니다. 그룹 또는 솔로 아티스트만 구독 가능합니다.");
        }

        LocalDateTime now = LocalDateTime.now();
        return membershipCacheRepository.findBySubscriberAndArtist(subscriber, artist)
                .map(membership -> {
                    membership.renew(now);
                    return membership;
                })
                .orElseGet(() -> membershipCacheRepository.save(
                        MembershipCache.create(subscriber, artist, MembershipStatus.ACTIVE, now.plusMonths(1))));
    }

    @Transactional
    public void cancel(Long membershipId, Long requesterId) {
        MembershipCache membership = membershipCacheRepository.findById(membershipId)
                .orElseThrow(() -> new InvalidRequestException("구독 정보를 찾을 수 없습니다."));
        if (!Objects.equals(membership.getSubscriber().getId(), requesterId)) {
            throw new InvalidRequestException("본인 구독만 취소할 수 있습니다.");
        }
        membership.cancel();
    }

    /**
     * 매일 자정 배치가 호출 — 만료일이 지난 ACTIVE 구독을 EXPIRED로 전환한다.
     */
    @Transactional
    public void expireOverdueMemberships(LocalDateTime now) {
        List<MembershipCache> overdue = membershipCacheRepository.findByStatusAndExpiresAtBefore(MembershipStatus.ACTIVE, now);
        overdue.forEach(MembershipCache::expire);
    }
}
