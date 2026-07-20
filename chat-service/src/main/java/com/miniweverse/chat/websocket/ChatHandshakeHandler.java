package com.miniweverse.chat.websocket;

import com.miniweverse.common.security.jwt.Role;
import java.security.Principal;
import java.util.Map;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

/**
 * JwtHandshakeInterceptor가 attributes에 남겨둔 신원 정보를 STOMP 세션의 Principal로 연결한다.
 */
public class ChatHandshakeHandler extends DefaultHandshakeHandler {

    @Override
    protected Principal determineUser(ServerHttpRequest request, WebSocketHandler wsHandler, Map<String, Object> attributes) {
        Long userId = (Long) attributes.get(JwtHandshakeInterceptor.USER_ID_ATTRIBUTE);
        Role role = (Role) attributes.get(JwtHandshakeInterceptor.ROLE_ATTRIBUTE);
        return new ChatPrincipal(userId, role);
    }
}
