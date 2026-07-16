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
 *
 * 팬의 fan-message SEND는 추가로 하루 5개 한도를 건다(위버스 DM 방식) — 이건 보안 위반이
 * 아니라 정책적 한도라, 예외를 던져 연결을 끊지 않고 그 메시지 하나만 조용히 버린다(null 반환).
 * 아티스트의 방송(artist-message)에는 이 한도가 없다.
 */
@Component
public class ChatChannelInterceptor implements ChannelInterceptor {

    private static final Pattern ROOM_DESTINATION_PATTERN = Pattern.compile("/(?:topic|app)/rooms/(\\d+)(?:/.*)?");
    private static final Pattern SEND_DESTINATION_PATTERN =
            Pattern.compile("/app/rooms/\\d+/(?:fan-message|artist-message)");

    private final ChatRoomRepository chatRoomRepository;
    private final MembershipVerifier membershipVerifier;
    private final FanMessageDailyQuota fanMessageDailyQuota;

    public ChatChannelInterceptor(
            ChatRoomRepository chatRoomRepository,
            MembershipVerifier membershipVerifier,
            FanMessageDailyQuota fanMessageDailyQuota
    ) {
        this.chatRoomRepository = chatRoomRepository;
        this.membershipVerifier = membershipVerifier;
        this.fanMessageDailyQuota = fanMessageDailyQuota;
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

        String destination = accessor.getDestination();
        Long artistId = extractArtistId(destination);
        if (artistId == null) {
            return message;
        }

        // SimpleBroker는 /topic으로 SEND된 프레임도 ChatMessageStompController를 거치지 않고
        // 그대로 구독자에게 릴레이한다. 방송(broadcast) 토픽으로의 직접 SEND를 막지 않으면
        // 권한 재검증·도배 방지·XSS 정제(ChatMessageService)가 전부 우회된다.
        if (command == StompCommand.SEND && !SEND_DESTINATION_PATTERN.matcher(destination).matches()) {
            throw new AccessDeniedException("이 채팅방에 접근할 권한이 없습니다.");
        }

        ChatPrincipal principal = accessor.getUser() instanceof ChatPrincipal chatPrincipal ? chatPrincipal : null;
        if (principal == null || !authorize(principal, artistId)) {
            throw new AccessDeniedException("이 채팅방에 접근할 권한이 없습니다.");
        }

        if (command == StompCommand.SEND && destination.endsWith("/fan-message")
                && !fanMessageDailyQuota.tryAcquire(principal.userId(), artistId)) {
            return null;
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
