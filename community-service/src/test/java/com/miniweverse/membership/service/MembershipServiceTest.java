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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * MembershipService는 @Service라 @DataJpaTest가 자동으로 빈을 만들어주지 않으므로, @Import로
 * 명시적으로 빈 등록에 포함시킨다. new MembershipService(...)로 직접 인스턴스화하면 Spring AOP
 * 프록시를 안 거치게 되어 @Transactional이 실제로 적용되지 않는다 — 그러면
 * findBySubscriberAndArtist의 PESSIMISTIC_WRITE 락이 subscribe() 메서드 전체가 아니라 그 조회
 * 하나의 트랜잭션에서만 유지되어, 동시성 테스트가 프로덕션 동작을 정확히 재현하지 못한다.
 */
@Tag("integration")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(MembershipService.class)
class MembershipServiceTest extends PostgresTestSupport {

    @Autowired
    private MembershipService membershipService;
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

    private User subscriber;
    private ArtistProfile artist;

    @BeforeEach
    void setUp() {
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

    /**
     * 최초 구독은 아직 존재하지 않는 row를 찾는 조회라 PESSIMISTIC_WRITE로 막히지 않는다(락을 걸
     * row 자체가 없음). 대신 두 스레드가 거의 동시에 INSERT를 시도하면 DB 유니크 제약
     * (uk_memberships_subscriber_artist)이 최종 방어선이 되어 하나만 성공하고 다른 하나는
     * MembershipService.createOrRenew의 catch 블록에서 InvalidRequestException으로 처리된다.
     * 이 결과(멤버십 정확히 1개)는 두 스레드의 실행 순서와 무관하게 항상 성립하므로, 타이밍에
     * 의존하는 낙관적 락 충돌 테스트와 달리 결정적으로 검증 가능하다.
     *
     * @DataJpaTest는 테스트 하나를 통째로 트랜잭션 하나로 감싸고 끝나면 롤백하는데, 그 트랜잭션이
     * 커밋되기 전이라 워커 스레드(각자 별도 트랜잭션)가 메인 스레드에서 만든 유저/아티스트를 아직
     * 못 본다. 그래서 이 테스트만 트랜잭션을 꺼서(NOT_SUPPORTED) setUp까지 포함해 전부 즉시
     * 커밋되게 하고, 자동 롤백이 없는 만큼 끝나고 직접 정리해 다음 테스트에 영향이 없게 한다.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void 동시에_두_스레드가_같은_대상을_최초_구독해도_멤버십은_정확히_1개만_생성된다() throws InterruptedException {
        User concurrentSubscriber = userRepository.saveAndFlush(
                User.createLocal("concurrent-fan@test.com", "pw", "concurrent-fan", Role.FAN));
        User concurrentArtistUser = userRepository.saveAndFlush(
                User.createLocal("concurrent-artist@test.com", "pw", "concurrent-artist", Role.ARTIST));
        ArtistProfile concurrentArtist = artistProfileRepository.saveAndFlush(
                ArtistProfile.create(concurrentArtistUser, "concurrent-channel", null, ArtistCategory.SOLO, null, null));

        try {
            int threadCount = 2;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        membershipService.subscribe(concurrentSubscriber.getId(), concurrentArtist.getId());
                    } catch (Exception ignored) {
                        // 동시 최초구독 중 하나는 유니크 제약 위반 → InvalidRequestException — 정상 동작
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }
            startLatch.countDown();
            doneLatch.await();
            executor.shutdown();

            List<Membership> memberships = membershipRepository.findAll().stream()
                    .filter(m -> m.getSubscriber().getId().equals(concurrentSubscriber.getId()))
                    .toList();
            assertThat(memberships).hasSize(1);
        } finally {
            // 이 테스트는 트랜잭션이 꺼져있어 자동 롤백이 안 되므로, setUp이 만든 것까지 포함해
            // 전부 지워서 다음 테스트가 깨끗한 상태에서 시작하게 한다(이메일 유니크 제약 충돌 방지).
            outboxEventRepository.deleteAll();
            membershipPeriodRepository.deleteAll();
            membershipRepository.deleteAll();
            artistProfileRepository.deleteAll();
            userRepository.deleteAll();
        }
    }
}
