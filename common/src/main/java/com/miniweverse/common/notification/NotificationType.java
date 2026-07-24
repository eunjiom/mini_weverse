package com.miniweverse.common.notification;

/**
 * community-service/chat-service(발행)와 notification-service(소비)가 공유하는 알림 종류.
 * 단일 Kafka 토픽에 이 타입 필드로 구분해서 싣는다(토픽을 종류별로 나누지 않기로 함).
 */
public enum NotificationType {
    NEW_FOLLOWER,
    MEMBERSHIP_ACTIVATED,
    MEMBERSHIP_EXPIRED,
    NEW_POST,
    NEW_COMMENT,
    NEW_CHAT_MESSAGE
}
