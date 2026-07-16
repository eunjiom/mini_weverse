package com.miniweverse.chat.websocket;

import java.security.Principal;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/** SessionUserRegistry를 연결/해제 시점에 채우고 비운다. */
@Component
public class SessionEventListener {

    private final SessionUserRegistry sessionUserRegistry;

    public SessionEventListener(SessionUserRegistry sessionUserRegistry) {
        this.sessionUserRegistry = sessionUserRegistry;
    }

    @EventListener
    public void handleSessionConnected(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal principal = accessor.getUser();
        if (principal instanceof ChatPrincipal chatPrincipal && accessor.getSessionId() != null) {
            sessionUserRegistry.register(accessor.getSessionId(), chatPrincipal.userId());
        }
    }

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        sessionUserRegistry.unregister(event.getSessionId());
    }
}
