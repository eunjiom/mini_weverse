package com.miniweverse.follow.repository;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.miniweverse.follow.entity.Follow;
import com.miniweverse.support.PostgresTestSupport;
import com.miniweverse.user.entity.User;
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
class FollowRepositoryTest extends PostgresTestSupport {

    @Autowired
    private FollowRepository followRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void 같은_대상을_중복_팔로우하면_유니크_제약_위반() {
        User follower = userRepository.saveAndFlush(User.createLocal("follower@test.com", "pw", "follower", Role.FAN));
        User artist = userRepository.saveAndFlush(User.createLocal("artist@test.com", "pw", "artist", Role.ARTIST));
        followRepository.saveAndFlush(Follow.create(follower, artist));

        Follow duplicate = Follow.create(follower, artist);

        assertThatThrownBy(() -> followRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
