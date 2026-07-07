package com.miniweverse.user.repository;

import com.miniweverse.user.entity.ArtistProfile;
import com.miniweverse.user.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArtistProfileRepository extends JpaRepository<ArtistProfile, Long> {

    Optional<ArtistProfile> findByUser(User user);
}
