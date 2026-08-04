package com.miniweverse.chat.broadcast;

import com.miniweverse.chat.dto.ChatMessageResponse;
import com.miniweverse.chat.membership.MembershipExpiredNotice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 다른 인스턴스(자기 자신 포함)가 Redis로 발행한 브로드캐스트 이벤트를 받아 로컬 STOMP
 * 브로커로 다시 전달한다. 대상 세션이 이 인스턴스에 없으면 스프링이 조용히 아무 일도
 * 하지 않으므로, 어느 인스턴스에 대상이 붙어있는지 별도로 추적할 필요가 없다.
 */
@Component
public class ChatBroadcastSubscriber implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(ChatBroadcastSubscriber.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    public ChatBroadcastSubscriber(SimpMessagingTemplate messagingTemplate, ObjectMapper objectMapper) {
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            ChatBroadcastEvent event = objectMapper.readValue(message.getBody(), ChatBroadcastEvent.class);
            Object payload = toPayload(event);
            if (event.routingType() == ChatBroadcastEvent.RoutingType.USER) {
                messagingTemplate.convertAndSendToUser(event.target(), event.destination(), payload);
            } else {
                messagingTemplate.convertAndSend(event.destination(), payload);
            }
        } catch (Exception e) {
            log.error("채팅 브로드캐스트 이벤트 처리 실패", e);
        }
    }

    private Object toPayload(ChatBroadcastEvent event) {
        return switch (event.payloadType()) {
            case CHAT_MESSAGE -> objectMapper.convertValue(event.payload(), ChatMessageResponse.class);
            case MEMBERSHIP_EXPIRED_NOTICE -> objectMapper.convertValue(event.payload(), MembershipExpiredNotice.class);
        };
    }
}
