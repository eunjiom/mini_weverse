package com.miniweverse.chat.websocket;

import com.miniweverse.chat.membership.MembershipVerifier;
import com.miniweverse.chat.repository.ChatRoomRepository;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * SUBSCRIBE/SEND 시점마다 "이 유저가 이 방(artistId)에 접근 가능한지"를 확인한다.
 * 아티스트인지는 JWT의 role 클레임이 아니라 ChatRoom.ownerUserId와 실제로 일치하는지로 판단한다
 * (역할 자기신고보다 리소스 소유 여부가 더 신뢰할 수 있는 판단 기준).
 */
@Component
public class ChatChannelInterceptor implements ChannelInterceptor {

    private static final Pattern ROOM_DESTINATION_PATTERN = Pattern.compile("/(?:topic|app)/rooms/(\\d+)(?:/.*)?");

    private final ChatRoomRepository chatRoomRepository;
    private final MembershipVerifier membershipVerifier;

    public ChatChannelInterceptor(ChatRoomRepository chatRoomRepository, MembershipVerifier membershipVerifier) {
        this.chatRoomRepository = chatRoomRepository;
        this.membershipVerifier = membershipVerifier;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();
        if (command != StompCommand.SUBSCRIBE && command != StompCommand.SEND) {
            return message;
        }

        Long artistId = extractArtistId(accessor.getDestination());
        if (artistId == null) {
            return message;
        }

        ChatPrincipal principal = accessor.getUser() instanceof ChatPrincipal chatPrincipal ? chatPrincipal : null;
        if (principal == null || !authorize(principal, artistId)) {
            throw new AccessDeniedException("이 채팅방에 접근할 권한이 없습니다.");
        }
        return message;
    }

    private boolean authorize(ChatPrincipal principal, Long artistId) {
        return chatRoomRepository.findByArtistId(artistId)
                .map(room -> {
                    if (room.getOwnerUserId().equals(principal.userId())) {
                        return true;
                    }
                    return membershipVerifier.isActiveMember(principal.userId(), artistId);
                })
                .orElse(false);
    }

    private Long extractArtistId(String destination) {
        if (destination == null) {
            return null;
        }
        Matcher matcher = ROOM_DESTINATION_PATTERN.matcher(destination);
        return matcher.matches() ? Long.valueOf(matcher.group(1)) : null;
    }
}
