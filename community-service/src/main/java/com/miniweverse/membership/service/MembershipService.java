package com.miniweverse.membership.service;

import com.miniweverse.exception.AuthUserExceptions.InvalidRequestException;
import com.miniweverse.membership.dto.MyMembershipResponse;
import com.miniweverse.membership.entity.Membership;
import com.miniweverse.membership.entity.MembershipPeriod;
import com.miniweverse.membership.outbox.MembershipOutboxEvent;
import com.miniweverse.membership.outbox.MembershipOutboxEventRepository;
import com.miniweverse.membership.repository.MembershipPeriodRepository;
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
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MembershipService {

    private final MembershipRepository membershipRepository;
    private final MembershipPeriodRepository membershipPeriodRepository;
    private final UserRepository userRepository;
    private final ArtistProfileRepository artistProfileRepository;
    private final MembershipOutboxEventRepository outboxEventRepository;

    public MembershipService(
            MembershipRepository membershipRepository,
            MembershipPeriodRepository membershipPeriodRepository,
            UserRepository userRepository,
            ArtistProfileRepository artistProfileRepository,
            MembershipOutboxEventRepository outboxEventRepository
    ) {
        this.membershipRepository = membershipRepository;
        this.membershipPeriodRepository = membershipPeriodRepository;
        this.userRepository = userRepository;
        this.artistProfileRepository = artistProfileRepository;
        this.outboxEventRepository = outboxEventRepository;
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
        Optional<Membership> existing = membershipRepository.findBySubscriberAndArtist(subscriber, artistProfile);
        Membership membership;
        LocalDateTime newPeriodStartedAt = null;
        if (existing.isPresent()) {
            membership = existing.get();
            boolean startedNewPeriod = membership.renew(now);
            if (startedNewPeriod) {
                membershipPeriodRepository.save(MembershipPeriod.start(membership, now));
                newPeriodStartedAt = now;
            }
        } else {
            membership = createOrRenew(subscriber, artistProfile, now);
            newPeriodStartedAt = now;
        }
        // chat-service 알림을 이 트랜잭션과 같이 커밋되는 아웃박스에 적재한다 — 알림 전송 자체가
        // 실패해도 구독 상태 변경과 분리되어 유실되지 않고, MembershipOutboxPublisher가 재시도한다.
        outboxEventRepository.save(MembershipOutboxEvent.activated(subscriber.getId(), artistProfile.getId(), newPeriodStartedAt));
        return membership;
    }

    private Membership createOrRenew(User subscriber, ArtistProfile artist, LocalDateTime now) {
        try {
            Membership membership = membershipRepository.save(
                    Membership.create(subscriber, artist, MembershipStatus.ACTIVE, now.plusMonths(1)));
            membershipPeriodRepository.save(MembershipPeriod.start(membership, now));
            return membership;
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
     * 매일 자정 배치가 호출 — 만료 대상 후보 id 목록만 조회한다. 실제 만료 처리는 {@link #expireOne}이
     * 건별로 따로 트랜잭션을 열어서 한다(이유는 그쪽 주석 참고).
     */
    @Transactional(readOnly = true)
    public List<Long> findOverdueMembershipIds(LocalDateTime now) {
        return membershipRepository.findByStatusAndExpiresAtBefore(MembershipStatus.ACTIVE, now).stream()
                .map(Membership::getId)
                .toList();
    }

    /**
     * 구독 하나를 만료 처리하고, 같은 트랜잭션에서 열려있던 기간을 닫고 chat-service 만료 알림을
     * 아웃박스에 적재한다. 건마다 별도 트랜잭션으로 커밋한다 — 배치가 후보를 조회한 시점과 이 메서드가
     * 실제로 커밋하는 시점 사이에 유저가 갱신을 먼저 커밋하면, Membership.version 충돌로 이 저장이
     * 실패한다. 그 실패를 이 메서드 안에서 복구하려 하지 않고 그대로 던져서 호출자(스케줄러)가 그
     * 건만 건너뛰게 한다 — 같은 트랜잭션 안에서 flush 실패 후 재조회/재시도를 하면 Hibernate 세션이
     * 오염될 수 있다는 걸 이전에 겪어봐서(구독 동시요청 버그), 여기서도 같은 방식을 피한다.
     */
    @Transactional
    public void expireOne(Long membershipId, LocalDateTime now) {
        Membership membership = membershipRepository.findById(membershipId).orElse(null);
        if (membership == null || membership.getStatus() != MembershipStatus.ACTIVE
                || membership.getExpiresAt() == null || !membership.getExpiresAt().isBefore(now)) {
            // 조회 이후 이미 갱신됐거나 삭제된 경우 — 더 이상 만료 대상이 아니므로 건너뜀
            return;
        }
        membership.expire();
        membershipPeriodRepository.findOpenPeriod(membership.getId())
                .ifPresent(period -> period.close(now));
        ArtistProfile artist = membership.getArtist();
        if (artist != null) {
            outboxEventRepository.save(MembershipOutboxEvent.expired(membership.getSubscriber().getId(), artist.getId(), now));
        }
    }
}
