package com.miniweverse.chat.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.miniweverse.chat.entity.ChatRoom;
import com.miniweverse.chat.membership.MembershipVerifier;
import com.miniweverse.chat.repository.ChatRoomRepository;
import com.miniweverse.common.security.jwt.Role;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class ChatChannelInterceptorTest {

    private static final Long OWNER_USER_ID = 100L;
    private static final Long ARTIST_ID = 1L;
    private static final Long FAN_USER_ID = 2L;

    @Mock
    private ChatRoomRepository chatRoomRepository;
    @Mock
    private MembershipVerifier membershipVerifier;
    @Mock
    private FanMessageDailyQuota fanMessageDailyQuota;

    private ChatChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new ChatChannelInterceptor(chatRoomRepository, membershipVerifier, fanMessageDailyQuota);
    }

    @Test
    void SUBSCRIBE_SEND가_아닌_프레임은_그대로_통과한다() {
        Message<?> message = stompMessage(StompCommand.CONNECT, "/topic/rooms/1", null);

        Message<?> result = interceptor.preSend(message, null);

        assertThat(result).isSameAs(message);
    }

    @Test
    void 방_소유자는_구독_시_멤버십_확인_없이_통과한다() {
        given(chatRoomRepository.findByArtistId(ARTIST_ID)).willReturn(Optional.of(chatRoom()));
        Message<?> message = stompMessage(StompCommand.SUBSCRIBE, "/topic/rooms/1", new ChatPrincipal(OWNER_USER_ID, Role.ARTIST));

        Message<?> result = interceptor.preSend(message, null);

        assertThat(result).isSameAs(message);
    }

    @Test
    void 활성_멤버십이_없는_팬은_구독_시_거부된다() {
        given(chatRoomRepository.findByArtistId(ARTIST_ID)).willReturn(Optional.of(chatRoom()));
        given(membershipVerifier.isActiveMember(FAN_USER_ID, ARTIST_ID)).willReturn(false);
        Message<?> message = stompMessage(StompCommand.SUBSCRIBE, "/topic/rooms/1", new ChatPrincipal(FAN_USER_ID, Role.FAN));

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void 방송_topic으로의_직접_SEND는_패턴이_안맞아_거부된다() {
        Message<?> message = stompMessage(StompCommand.SEND, "/topic/rooms/1/broadcast", new ChatPrincipal(FAN_USER_ID, Role.FAN));

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void 활성_멤버인_팬의_fan_message_SEND는_쿼터가_남아있으면_통과한다() {
        given(chatRoomRepository.findByArtistId(ARTIST_ID)).willReturn(Optional.of(chatRoom()));
        given(membershipVerifier.isActiveMember(FAN_USER_ID, ARTIST_ID)).willReturn(true);
        given(fanMessageDailyQuota.tryAcquire(FAN_USER_ID, ARTIST_ID)).willReturn(true);
        Message<?> message = stompMessage(StompCommand.SEND, "/app/rooms/1/fan-message", new ChatPrincipal(FAN_USER_ID, Role.FAN));

        Message<?> result = interceptor.preSend(message, null);

        assertThat(result).isSameAs(message);
    }

    @Test
    void fan_message_SEND가_하루_쿼터를_초과하면_예외_없이_메시지만_버려진다() {
        given(chatRoomRepository.findByArtistId(ARTIST_ID)).willReturn(Optional.of(chatRoom()));
        given(membershipVerifier.isActiveMember(FAN_USER_ID, ARTIST_ID)).willReturn(true);
        given(fanMessageDailyQuota.tryAcquire(FAN_USER_ID, ARTIST_ID)).willReturn(false);
        Message<?> message = stompMessage(StompCommand.SEND, "/app/rooms/1/fan-message", new ChatPrincipal(FAN_USER_ID, Role.FAN));

        Message<?> result = interceptor.preSend(message, null);

        assertThat(result).isNull();
    }

    /**
     * ChatRoom은 애플리케이션 코드로 생성하는 경로가 없어(DB에 직접 생성) protected 기본
     * 생성자만 있다 — 테스트에서 값을 채우려면 리플렉션으로 직접 필드를 설정할 수밖에 없다.
     */
    private ChatRoom chatRoom() {
        try {
            var constructor = ChatRoom.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            ChatRoom room = constructor.newInstance();
            setField(room, "artistId", ChatChannelInterceptorTest.ARTIST_ID);
            setField(room, "ownerUserId", ChatChannelInterceptorTest.OWNER_USER_ID);
            return room;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private void setField(Object target, String fieldName, Object value) throws ReflectiveOperationException {
        var field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private Message<?> stompMessage(StompCommand command, String destination, ChatPrincipal principal) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setDestination(destination);
        if (principal != null) {
            accessor.setUser(principal);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
