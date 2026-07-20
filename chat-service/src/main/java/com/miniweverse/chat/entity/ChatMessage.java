package com.miniweverse.chat.entity;

import com.miniweverse.common.BaseTimeEntity;
import com.miniweverse.exception.ChatExceptions.InvalidRequestException;
import com.miniweverse.common.security.jwt.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

/**
 * 위버스 DM 방식 가시성: senderRole=ARTIST인 메시지는 모두에게 보이고(방송), FAN 메시지는
 * 보낸 본인 + 아티스트에게만 보인다. 팬별로 메시지를 복제 저장하지 않고, 조회 시점에
 * ChatMessageRepository의 필터 쿼리로 이 규칙을 적용한다.
 *
 * content는 순수 텍스트만 허용한다 — 저장 전에 HTML 태그를 전부 제거해서, 프론트가 이 값을
 * 안전하지 않게(innerHTML 등) 렌더링하더라도 스크립트 삽입(XSS)이 실행되지 않게 한다.
 */
@Entity
@Table(name = "chat_messages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private ChatRoom room;

    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "sender_role", nullable = false)
    private Role senderRole;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    private ChatMessage(ChatRoom room, Long senderId, Role senderRole, String content) {
        this.room = room;
        this.senderId = senderId;
        this.senderRole = senderRole;
        this.content = content;
    }

    public static ChatMessage create(ChatRoom room, Long senderId, Role senderRole, String content) {
        if (room == null || senderId == null || senderRole == null) {
            throw new InvalidRequestException("room, senderId, senderRole는 필수입니다.");
        }
        String sanitized = content == null ? null : Jsoup.clean(content, Safelist.none());
        if (sanitized == null || sanitized.isBlank()) {
            throw new InvalidRequestException("메시지 내용은 비어있을 수 없습니다.");
        }
        return new ChatMessage(room, senderId, senderRole, sanitized);
    }
}
