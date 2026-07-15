package com.miniweverse.membership.service;

import com.miniweverse.exception.AuthUserExceptions.InvalidRequestException;
import com.miniweverse.membership.dto.MyMembershipResponse;
import com.miniweverse.membership.entity.Membership;
import com.miniweverse.membership.repository.MembershipRepository;
import com.miniweverse.user.entity.ArtistProfile;
import com.miniweverse.user.entity.User;
import com.miniweverse.user.enums.ArtistCategory;
import com.miniweverse.user.enums.MembershipStatus;
import com.miniweverse.user.repository.ArtistProfileRepository;
import com.miniweverse.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MembershipService {

    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final ArtistProfileRepository artistProfileRepository;

    public MembershipService(
            MembershipRepository membershipRepository,
            UserRepository userRepository,
            ArtistProfileRepository artistProfileRepository
    ) {
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.artistProfileRepository = artistProfileRepository;
    }

    @Transactional
    public Membership subscribe(Long subscriberId, Long artistProfileId) {
        User subscriber = userRepository.findById(subscriberId)
                .orElseThrow(() -> new InvalidRequestException("구독자 정보를 찾을 수 없습니다."));
        ArtistProfile artistProfile = artistProfileRepository.findById(artistProfileId)
                .orElseThrow(() -> new InvalidRequestException("아티스트 프로필을 찾을 수 없습니다."));
        if (artistProfile.getCategory() == ArtistCategory.MEMBER) {
            throw new InvalidRequestException("그룹 멤버는 구독할 수 없습니다. 그룹 또는 솔로 아티스트만 구독 가능합니다.");
        }

        // findBySubscriberAndArtist는 PESSIMISTIC_WRITE 락을 걸기 때문에, 동시에 들어온
        // 같은 (subscriber, artist) 요청은 여기서 순서대로 직렬화된다.
        LocalDateTime now = LocalDateTime.now();
        return membershipRepository.findBySubscriberAndArtist(subscriber, artistProfile)
                .map(membership -> {
                    membership.renew(now);
                    return membership;
                })
                .orElseGet(() -> createOrRenew(subscriber, artistProfile, now));
    }

    private Membership createOrRenew(User subscriber, ArtistProfile artist, LocalDateTime now) {
        try {
            return membershipRepository.save(
                    Membership.create(subscriber, artist, MembershipStatus.ACTIVE, now.plusMonths(1)));
        } catch (DataIntegrityViolationException e) {
            // 동시 요청으로 최초 구독 확인(락을 걸 row가 아직 없던 시점)을 통과한 뒤 유니크 제약에서 걸린 경우.
            // 이미 flush가 실패해서 현재 트랜잭션의 영속성 컨텍스트는 더 이상 안전하게 쓸 수 없으므로,
            // 같은 트랜잭션에서 재조회/갱신을 시도하지 않고 깨끗하게 실패시킨다. 클라이언트가 재시도하면
            // 이번엔 이미 생성된 row를 findBySubscriberAndArtist가 락을 걸고 정상적으로 갱신한다.
            throw new InvalidRequestException("이미 구독 처리 중입니다. 잠시 후 다시 시도해주세요.");
        }
    }

    @Transactional(readOnly = true)
    public List<MyMembershipResponse> getMyMemberships(Long subscriberId) {
        userRepository.findById(subscriberId)
                .orElseThrow(() -> new InvalidRequestException("유저 정보를 찾을 수 없습니다."));
        return membershipRepository.findBySubscriberIdWithArtist(subscriberId).stream()
                .map(MyMembershipResponse::from)
                .toList();
    }

    @Transactional
    public void cancel(Long membershipId, Long requesterId) {
        Membership membership = membershipRepository.findByIdForUpdate(membershipId)
                .orElseThrow(() -> new InvalidRequestException("구독 정보를 찾을 수 없습니다."));
        if (!Objects.equals(membership.getSubscriber().getId(), requesterId)) {
            throw new InvalidRequestException("본인 구독만 취소할 수 있습니다.");
        }
        membership.cancel();
    }

    @Transactional(readOnly = true)
    public boolean isActive(Long subscriberId, Long artistId) {
        return membershipRepository.existsBySubscriberIdAndArtistIdAndStatus(subscriberId, artistId, MembershipStatus.ACTIVE);
    }

    /**
     * 매일 자정 배치가 호출 — 만료일이 지난 ACTIVE 구독을 EXPIRED로 전환한다.
     * 호출자(스케줄러)가 이 메서드(트랜잭션) 반환 후에 chat-service로 만료 알림을 보낼 수 있도록,
     * 방금 만료시킨 목록을 그대로 반환한다.
     */
    @Transactional
    public List<Membership> expireOverdueMemberships(LocalDateTime now) {
        List<Membership> overdue = membershipRepository.findByStatusAndExpiresAtBefore(MembershipStatus.ACTIVE, now);
        overdue.forEach(Membership::expire);
        return overdue;
    }
}
