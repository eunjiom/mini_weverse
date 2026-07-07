package com.miniweverse.user.repository;

import com.miniweverse.user.entity.Follow;
import com.miniweverse.user.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FollowRepository extends JpaRepository<Follow, Long> {

    Optional<Follow> findByFollowerAndArtist(User follower, User artist);
}
