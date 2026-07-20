package com.miniweverse.chat.repository;

import com.miniweverse.chat.entity.ChatMessage;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /**
     * 위버스 DM 가시성 규칙: 아티스트가 보낸 메시지(방송)는 전부, 그 외엔 fanUserId 본인이 보낸
     * 메시지만 보인다. 팬이 자기 스레드를 볼 때도, 아티스트가 특정 팬의 스레드를 볼 때도 동일 쿼리.
     * ID 기준 커서 페이지네이션(Post/Comment와 동일한 컨벤션) — cursor가 null이면 최신 메시지부터,
     * 있으면 그 id보다 작은(더 오래된) 메시지를 가져온다. Pageable은 LIMIT(개수 제한)에만 쓴다.
     */
    @Query("""
            SELECT m FROM ChatMessage m
            WHERE m.room.id = :roomId
              AND (m.senderRole = com.miniweverse.common.security.jwt.Role.ARTIST OR m.senderId = :fanUserId)
              AND (:cursor IS NULL OR m.id < :cursor)
            ORDER BY m.id DESC
            """)
    List<ChatMessage> findVisibleMessages(
            @Param("roomId") Long roomId,
            @Param("fanUserId") Long fanUserId,
            @Param("cursor") Long cursor,
            Pageable pageable
    );
}
