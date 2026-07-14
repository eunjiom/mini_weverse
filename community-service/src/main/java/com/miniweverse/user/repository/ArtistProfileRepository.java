package com.miniweverse.user.repository;

import com.miniweverse.user.entity.ArtistProfile;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArtistProfileRepository extends JpaRepository<ArtistProfile, Long> {

    List<ArtistProfile> findByChannelNameContainingIgnoreCaseOrderByIdDesc(String channelName, Pageable pageable);
}
