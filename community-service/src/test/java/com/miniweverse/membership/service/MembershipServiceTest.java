package com.miniweverse.membership.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.miniweverse.common.security.jwt.Role;
import com.miniweverse.membership.entity.Membership;
import com.miniweverse.membership.outbox.MembershipOutboxEvent;
import com.miniweverse.membership.outbox.MembershipOutboxEventRepository;
import com.miniweverse.membership.outbox.MembershipOutboxEventType;
import com.miniweverse.membership.outbox.OutboxEventStatus;
import com.miniweverse.membership.repository.MembershipPeriodRepository;
import com.miniweverse.membership.repository.MembershipRepository;
import com.miniweverse.support.PostgresTestSupport;
import com.miniweverse.user.entity.ArtistProfile;
import com.miniweverse.user.entity.User;
import com.miniweverse.user.enums.ArtistCategory;
import com.miniweverse.user.enums.MembershipStatus;
import com.miniweverse.user.repository.ArtistProfileRepository;
import com.miniweverse.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.PageRequest;

/**
 * MembershipService는 @Service라 @DataJpaTest가 자동으로 빈을 만들어주지 않으므로,
 * @DataJpaTest가 띄워준 실제(Postgres Testcontainers) 리포지토리들을 그대로 생성자에 주입해
 * 직접 인스턴스화한다. Redis 등 다른 인프라 없이 JPA 계층만 검증하려는 의도.
 */
@Tag("integration")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MembershipServiceTest extends PostgresTestSupport {

    @Autowired
    private MembershipRepository membershipRepository;
    @Autowired
    private MembershipPeriodRepository membershipPeriodRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ArtistProfileRepository artistProfileRepository;
    @Autowired
    private MembershipOutboxEventRepository outboxEventRepository;

    private MembershipService membershipService;
    private User subscriber;
    private ArtistProfile artist;

    @BeforeEach
    void setUp() {
        membershipService = new MembershipService(
                membershipRepository, membershipPeriodRepository, userRepository, artistProfileRepository, outboxEventRepository
        );
        subscriber = userRepository.saveAndFlush(User.createLocal("fan@test.com", "pw", "fan", Role.FAN));
        User artistUser = userRepository.saveAndFlush(User.createLocal("artist@test.com", "pw", "artist", Role.ARTIST));
        artist = artistProfileRepository.saveAndFlush(
                ArtistProfile.create(artistUser, "channel", null, ArtistCategory.SOLO, null, null));
    }

    @Test
    void 최초_구독_시_활성_멤버십과_기간_이력과_아웃박스_활성화_이벤트가_생성된다() {
        Membership membership = membershipService.subscribe(subscriber.getId(), artist.getId());

        assertThat(membership.getStatus()).isEqualTo(MembershipStatus.ACTIVE);
        assertThat(membershipPeriodRepository.findOpenPeriod(membership.getId())).isPresent();

        List<MembershipOutboxEvent> events = outboxEventRepository.findByStatusOrderByIdAsc(OutboxEventStatus.PENDING, PageRequest.of(0, 10));
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getEventType()).isEqualTo(MembershipOutboxEventType.MEMBERSHIP_ACTIVATED);
        assertThat(events.get(0).getFanUserId()).isEqualTo(subscriber.getId());
        assertThat(events.get(0).getArtistId()).isEqualTo(artist.getId());
    }

    @Test
    void 만료_전에_다시_구독하면_새_기간을_열지_않고_기존_멤버십을_연장한다() {
        Membership first = membershipService.subscribe(subscriber.getId(), artist.getId());
        LocalDateTime firstExpiresAt = first.getExpiresAt();

        Membership renewed = membershipService.subscribe(subscriber.getId(), artist.getId());

        assertThat(renewed.getId()).isEqualTo(first.getId());
        assertThat(renewed.getExpiresAt()).isAfter(firstExpiresAt);
        assertThat(membershipRepository.findBySubscriberAndArtist(subscriber, artist)).hasValueSatisfying(
                m -> assertThat(m.getId()).isEqualTo(first.getId())
        );
    }

    @Test
    void expireOne은_아직_만료일이_지나지_않은_멤버십은_건드리지_않는다() {
        Membership membership = membershipService.subscribe(subscriber.getId(), artist.getId());
        LocalDateTime notYetExpired = membership.getExpiresAt().minusDays(1);

        membershipService.expireOne(membership.getId(), notYetExpired);

        Membership reloaded = membershipRepository.findById(membership.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(MembershipStatus.ACTIVE);
    }

    @Test
    void expireOne은_만료일이_지난_멤버십을_만료시키고_기간을_닫고_만료_이벤트를_적재한다() {
        Membership membership = membershipService.subscribe(subscriber.getId(), artist.getId());
        LocalDateTime afterExpiry = membership.getExpiresAt().plusDays(1);

        membershipService.expireOne(membership.getId(), afterExpiry);

        Membership reloaded = membershipRepository.findById(membership.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(MembershipStatus.EXPIRED);
        assertThat(membershipPeriodRepository.findOpenPeriod(membership.getId())).isEmpty();

        List<MembershipOutboxEvent> expiredEvents = outboxEventRepository.findByStatusOrderByIdAsc(OutboxEventStatus.PENDING, PageRequest.of(0, 10))
                .stream()
                .filter(e -> e.getEventType() == MembershipOutboxEventType.MEMBERSHIP_EXPIRED)
                .toList();
        assertThat(expiredEvents).hasSize(1);
    }
}
