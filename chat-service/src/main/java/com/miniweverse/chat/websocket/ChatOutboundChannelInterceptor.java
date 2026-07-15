package com.miniweverse.chat.websocket;

import com.miniweverse.chat.membership.MembershipVerifier;
import com.miniweverse.chat.repository.ChatRoomRepository;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

/**
 * 아티스트 방송(/topic/rooms/{artistId}/broadcast)이 나가는 시점마다, 받는 사람이 지금도
 * 자격이 있는지 매번 확인한다. SUBSCRIBE 시점 검사만으로는 "구독한 뒤 멤버십이 만료된 팬"을
 * 걸러내지 못하기 때문 — 구독 자체를 강제로 끊는 대신, 그 팬에게만 배달을 조용히 생략한다
 * (null 반환). 자정 만료 알림(MembershipVerifier.markExpired)이 캐시를 즉시 정정해두므로
 * 이 체크는 대부분 캐시 히트로 끝난다.
 *
 * clientOutboundChannel의 메시지는 아직 실제 STOMP 프레임으로 직렬화되기 전(그 변환은
 * StompSubProtocolHandler가 이 채널을 통과한 뒤에 함)이라, StompHeaderAccessor가 아니라
 * 더 일반적인 SimpMessageHeaderAccessor로 읽어야 한다.
 */
@Component
public class ChatOutboundChannelInterceptor implements ChannelInterceptor {

    private static final Pattern BROADCAST_DESTINATION_PATTERN = Pattern.compile("/topic/rooms/(\\d+)/broadcast");

    private final SessionUserRegistry sessionUserRegistry;
    private final MembershipVerifier membershipVerifier;
    private final ChatRoomRepository chatRoomRepository;

    public ChatOutboundChannelInterceptor(
            SessionUserRegistry sessionUserRegistry,
            MembershipVerifier membershipVerifier,
            ChatRoomRepository chatRoomRepository
    ) {
        this.sessionUserRegistry = sessionUserRegistry;
        this.membershipVerifier = membershipVerifier;
        this.chatRoomRepository = chatRoomRepository;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.wrap(message);
        if (accessor.getMessageType() != SimpMessageType.MESSAGE) {
            return message;
        }

        Long artistId = extractArtistId(accessor.getDestination());
        if (artistId == null) {
            return message;
        }

        String sessionId = accessor.getSessionId();
        Long userId = sessionId != null ? sessionUserRegistry.getUserId(sessionId) : null;
        if (userId == null) {
            return message;
        }

        if (isRoomOwner(userId, artistId) || membershipVerifier.isActiveMember(userId, artistId)) {
            return message;
        }
        return null;
    }

    private boolean isRoomOwner(Long userId, Long artistId) {
        return chatRoomRepository.findByArtistId(artistId)
                .map(room -> room.getOwnerUserId().equals(userId))
                .orElse(false);
    }

    private Long extractArtistId(String destination) {
        if (destination == null) {
            return null;
        }
        Matcher matcher = BROADCAST_DESTINATION_PATTERN.matcher(destination);
        return matcher.matches() ? Long.valueOf(matcher.group(1)) : null;
    }
}
