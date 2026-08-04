package com.miniweverse.chat.broadcast;

import com.miniweverse.chat.dto.ChatMessageResponse;
import com.miniweverse.chat.membership.MembershipExpiredNotice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * chat-service가 여러 인스턴스로 늘어나면 STOMP 심플 브로커는 인스턴스별 인메모리라
 * 로컬로만 보내는 것으로는 다른 인스턴스에 붙은 세션까지 전달되지 않는다. 그래서 실제
 * 전송 전에 Redis 채널로 먼저 발행해, 모든 인스턴스가 ChatBroadcastSubscriber를 통해
 * 각자 로컬 세션에 다시 전달하게 한다(발행한 인스턴스 자신도 동일하게 구독해서 받는다).
 */
@Component
public class ChatBroadcastPublisher {

    private static final Logger log = LoggerFactory.getLogger(ChatBroadcastPublisher.class);

    private final StringRedisTemplate redisTemplate;
    private final ChannelTopic topic;
    private final ObjectMapper objectMapper;

    public ChatBroadcastPublisher(
            StringRedisTemplate redisTemplate,
            ChannelTopic chatBroadcastTopic,
            ObjectMapper objectMapper
    ) {
        this.redisTemplate = redisTemplate;
        this.topic = chatBroadcastTopic;
        this.objectMapper = objectMapper;
    }

    public void toTopic(String destination, ChatMessageResponse payload) {
        publish(ChatBroadcastEvent.toTopic(destination, ChatBroadcastEvent.PayloadType.CHAT_MESSAGE, payload));
    }

    public void toUser(String target, String destination, ChatMessageResponse payload) {
        publish(ChatBroadcastEvent.toUser(target, destination, ChatBroadcastEvent.PayloadType.CHAT_MESSAGE, payload));
    }

    public void toUser(String target, String destination, MembershipExpiredNotice payload) {
        publish(ChatBroadcastEvent.toUser(target, destination, ChatBroadcastEvent.PayloadType.MEMBERSHIP_EXPIRED_NOTICE, payload));
    }

    // 메시지 자체는 이미 DB에 저장된 뒤라, 브로드캐스트 발행 실패가 이 요청/세션 전체를
    // 실패시킬 이유는 없다 — 로그만 남기고 흡수한다(MembershipCache와 동일한 fail-safe 방침).
    private void publish(ChatBroadcastEvent event) {
        try {
            redisTemplate.convertAndSend(topic.getTopic(), objectMapper.writeValueAsString(event));
        } catch (JacksonException e) {
            log.error("채팅 브로드캐스트 이벤트 직렬화 실패, event={}", event, e);
        } catch (DataAccessException e) {
            log.error("채팅 브로드캐스트 이벤트 발행 실패(Redis), event={}", event, e);
        }
    }
}
