package com.miniweverse.membership.repository;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.miniweverse.membership.entity.Membership;
import com.miniweverse.support.PostgresTestSupport;
import com.miniweverse.user.entity.User;
import com.miniweverse.user.enums.MembershipStatus;
import com.miniweverse.user.enums.Role;
import com.miniweverse.user.repository.UserRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;

@Tag("integration")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MembershipRepositoryTest extends PostgresTestSupport {

    @Autowired
    private MembershipRepository membershipRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void 같은_구독_대상이_중복되면_유니크_제약_위반() {
        User subscriber = userRepository.saveAndFlush(User.createLocal("sub@test.com", "pw", "sub", Role.FAN));
        User artist = userRepository.saveAndFlush(User.createLocal("artist2@test.com", "pw", "artist2", Role.ARTIST));
        membershipRepository.saveAndFlush(
                Membership.create(subscriber, artist, MembershipStatus.ACTIVE, null));

        Membership duplicate = Membership.create(subscriber, artist, MembershipStatus.ACTIVE, null);

        assertThatThrownBy(() -> membershipRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
