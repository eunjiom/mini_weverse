package com.miniweverse.chat.repository;

import com.miniweverse.chat.entity.ChatRoom;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    Optional<ChatRoom> findByArtistId(Long artistId);
}
