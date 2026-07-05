package com.miniweverse.user.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.miniweverse.support.PostgresTestSupport;
import com.miniweverse.user.Role;
import com.miniweverse.user.User;
import java.util.Optional;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

@Tag("integration")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryTest extends PostgresTestSupport {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void 탈퇴한_유저는_조회되지_않는다() {
        User user = User.createLocal("delete@test.com", "pw", "nick", Role.FAN);
        userRepository.saveAndFlush(user);
        Long id = user.getId();

        user.delete();
        userRepository.saveAndFlush(user);
        entityManager.clear();

        Optional<User> found = userRepository.findById(id);

        assertThat(found).isEmpty();
    }

    @Test
    void 탈퇴한_유저의_이메일은_재사용_가능하다() {
        User user = User.createLocal("reuse@test.com", "pw", "nick1", Role.FAN);
        userRepository.saveAndFlush(user);
        user.delete();
        userRepository.saveAndFlush(user);
        entityManager.clear();

        User newUser = User.createLocal("reuse@test.com", "pw2", "nick2", Role.FAN);

        assertThatCode(() -> userRepository.saveAndFlush(newUser)).doesNotThrowAnyException();
    }

    @Test
    void 탈퇴하지_않은_유저는_이메일이_중복되면_예외() {
        User user = User.createLocal("dup@test.com", "pw", "nick1", Role.FAN);
        userRepository.saveAndFlush(user);

        User duplicate = User.createLocal("dup@test.com", "pw2", "nick2", Role.FAN);

        assertThatThrownBy(() -> userRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
