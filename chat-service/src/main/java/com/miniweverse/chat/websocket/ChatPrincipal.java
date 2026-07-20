package com.miniweverse.chat.websocket;

import com.miniweverse.common.security.jwt.Role;
import java.security.Principal;

/**
 * WebSocket 세션 하나에 대응하는 인증 주체. STOMP 메시지 핸들러에서 Principal로 주입받아
 * 누가 보낸 메시지인지 식별하는 데 쓴다.
 */
public record ChatPrincipal(Long userId, Role role) implements Principal {

    @Override
    public String getName() {
        return String.valueOf(userId);
    }
}
