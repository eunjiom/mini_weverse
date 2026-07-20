package com.miniweverse.chat.repository;

import com.miniweverse.chat.entity.ChatMessage;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /**
     * 위버스 DM 가시성 규칙: 아티스트가 보낸 메시지(방송)는 fanUserId가 그 시점에 실제로 구독
     * 기간 안이었을 때만, 그 외엔 fanUserId 본인이 보낸 메시지만 보인다(팬 메시지는 애초에 활성
     * 구독자만 보낼 수 있어서 항상 본인 구독 기간 안이므로 별도 기간 체크가 필요 없다).
     * ID 기준 커서 페이지네이션(Post/Comment와 동일한 컨벤션) — cursor가 null이면 최신 메시지부터,
     * 있으면 그 id보다 작은(더 오래된) 메시지를 가져온다. Pageable은 LIMIT(개수 제한)에만 쓴다.
     */
    @Query("""
            SELECT m FROM ChatMessage m
            JOIN FETCH m.room
            WHERE m.room.id = :roomId
              AND (
                (m.senderRole = com.miniweverse.common.security.jwt.Role.ARTIST AND EXISTS (
                    SELECT 1 FROM MembershipPeriod p
                    WHERE p.fanUserId = :fanUserId AND p.artistId = :artistId
                      AND p.startedAt <= m.createdAt
                      AND (p.endedAt IS NULL OR p.endedAt > m.createdAt)
                ))
                OR m.senderId = :fanUserId
              )
              AND (:cursor IS NULL OR m.id < :cursor)
            ORDER BY m.id DESC
            """)
    List<ChatMessage> findVisibleMessages(
            @Param("roomId") Long roomId,
            @Param("artistId") Long artistId,
            @Param("fanUserId") Long fanUserId,
            @Param("cursor") Long cursor,
            Pageable pageable
    );

    /**
     * 아티스트 인박스 전용 — 방 주인은 팬 필터 없이 방에 오간 메시지 전부를 볼 자격이 있으므로
     * findVisibleMessages와 달리 가시성 조건이 없다.
     */
    @Query("""
            SELECT m FROM ChatMessage m
            JOIN FETCH m.room
            WHERE m.room.id = :roomId
              AND (:cursor IS NULL OR m.id < :cursor)
            ORDER BY m.id DESC
            """)
    List<ChatMessage> findAllByRoomId(
            @Param("roomId") Long roomId,
            @Param("cursor") Long cursor,
            Pageable pageable
    );
}
