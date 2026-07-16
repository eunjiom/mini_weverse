package com.miniweverse.chat.websocket;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * STOMP 세션ID → userId 매핑. ChatOutboundChannelInterceptor가 "지금 이 메시지를 받을 세션이
 * 누구인지"를 빠르게(O(1)) 찾기 위한 용도 — SimpUserRegistry는 세션ID로 역조회하는 API가 없어서
 * 직접 둔다.
 */
@Component
public class SessionUserRegistry {

    private final Map<String, Long> userIdBySessionId = new ConcurrentHashMap<>();

    public void register(String sessionId, Long userId) {
        userIdBySessionId.put(sessionId, userId);
    }

    public void unregister(String sessionId) {
        userIdBySessionId.remove(sessionId);
    }

    public Long getUserId(String sessionId) {
        return userIdBySessionId.get(sessionId);
    }
}
