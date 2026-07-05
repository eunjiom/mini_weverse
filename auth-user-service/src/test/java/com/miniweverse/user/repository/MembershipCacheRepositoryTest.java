package com.miniweverse.user.repository;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.miniweverse.support.PostgresTestSupport;
import com.miniweverse.user.MembershipCache;
import com.miniweverse.user.MembershipStatus;
import com.miniweverse.user.Role;
import com.miniweverse.user.User;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;

@Tag("integration")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MembershipCacheRepositoryTest extends PostgresTestSupport {

    @Autowired
    private MembershipCacheRepository membershipCacheRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void 같은_구독_대상의_캐시가_중복되면_유니크_제약_위반() {
        User subscriber = userRepository.saveAndFlush(User.createLocal("sub@test.com", "pw", "sub", Role.FAN));
        User artist = userRepository.saveAndFlush(User.createLocal("artist2@test.com", "pw", "artist2", Role.ARTIST));
        membershipCacheRepository.saveAndFlush(
                MembershipCache.create(subscriber, artist, MembershipStatus.ACTIVE, null));

        MembershipCache duplicate = MembershipCache.create(subscriber, artist, MembershipStatus.ACTIVE, null);

        assertThatThrownBy(() -> membershipCacheRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
