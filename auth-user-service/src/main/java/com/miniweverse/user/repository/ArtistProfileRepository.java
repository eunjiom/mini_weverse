package com.miniweverse.user.repository;

import com.miniweverse.user.entity.ArtistProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArtistProfileRepository extends JpaRepository<ArtistProfile, Long> {
}
