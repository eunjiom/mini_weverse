package com.miniweverse.chat.dto;

import com.miniweverse.chat.entity.ChatMessage;
import com.miniweverse.common.security.jwt.Role;
import java.time.LocalDateTime;

public record ChatMessageResponse(
        Long id,
        Long artistId,
        Long senderId,
        Role senderRole,
        String content,
        LocalDateTime createdAt
) {
    public static ChatMessageResponse from(ChatMessage message) {
        return new ChatMessageResponse(
                message.getId(),
                message.getRoom().getArtistId(),
                message.getSenderId(),
                message.getSenderRole(),
                message.getContent(),
                message.getCreatedAt()
        );
    }
}
