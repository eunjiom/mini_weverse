package com.miniweverse.chat.repository;

import com.miniweverse.chat.entity.ChatMessage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /**
     * 위버스 DM 가시성 규칙: 아티스트가 보낸 메시지(방송)는 전부, 그 외엔 fanUserId 본인이 보낸
     * 메시지만 보인다. 팬이 자기 스레드를 볼 때도, 아티스트가 특정 팬의 스레드를 볼 때도 동일 쿼리.
     */
    @Query("""
            SELECT m FROM ChatMessage m
            WHERE m.room.id = :roomId
              AND (m.senderRole = com.miniweverse.user.enums.Role.ARTIST OR m.senderId = :fanUserId)
            ORDER BY m.createdAt ASC, m.id ASC
            """)
    List<ChatMessage> findVisibleMessages(@Param("roomId") Long roomId, @Param("fanUserId") Long fanUserId);
}
