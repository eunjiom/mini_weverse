package com.miniweverse.chat.broadcast;

public record ChatBroadcastEvent(
        RoutingType routingType,
        String target,
        String destination,
        PayloadType payloadType,
        Object payload
) {

    public enum RoutingType { TOPIC, USER }

    /** 구독 인스턴스가 payload(역직렬화 시 Map)를 어떤 DTO로 되돌릴지 판단하는 기준. */
    public enum PayloadType { CHAT_MESSAGE, MEMBERSHIP_EXPIRED_NOTICE }

    public static ChatBroadcastEvent toTopic(String destination, PayloadType payloadType, Object payload) {
        return new ChatBroadcastEvent(RoutingType.TOPIC, null, destination, payloadType, payload);
    }

    public static ChatBroadcastEvent toUser(String target, String destination, PayloadType payloadType, Object payload) {
        return new ChatBroadcastEvent(RoutingType.USER, target, destination, payloadType, payload);
    }
}
